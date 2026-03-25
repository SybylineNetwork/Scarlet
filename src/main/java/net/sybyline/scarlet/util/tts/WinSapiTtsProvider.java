package net.sybyline.scarlet.util.tts;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * um\sapi.h
 */
public class WinSapiTtsProvider implements TtsProvider
{

    public static final String NaturalVoiceSAPIAdapter_URL = "https://github.com/gexgd0419/NaturalVoiceSAPIAdapter";

    public WinSapiTtsProvider(Path dir) throws InterruptedException, ExecutionException
    {
        this.dir = TtsProviderUtil.checkDir(dir);
        this.executor.submit(this::initVoices).get();
    }

    private final Path dir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(TtsThread::new);
    private final Map<String, Invoker> voiceById = new LinkedHashMap<>();
    private final List<String> voices = new ArrayList<>();
    private volatile Invoker synth = null;

    private void initVoices()
    {
        PointerByReference ppv = new PointerByReference();
        try (Invoker spOTC = new Invoker(CLSID_SpObjectTokenCategory, IID_ISpObjectTokenCategory))
        {
            spOTC.invokeCheckHRESULT(ISpObjectTokenCategory_SetId, spOTC, VOICES_CATEGORY, (short)0);
            spOTC.invokeCheckHRESULT(ISpObjectTokenCategory_EnumTokens, spOTC, null, null, ppv);
            try (Invoker EspOT = new Invoker(ppv.getValue()))
            {
                IntByReference fetched = new IntByReference();
                while (WinNT.S_OK.equals(EspOT.invokeHRESULT(IEnumSpObjectTokens_Next, EspOT, 1, ppv, fetched)) && fetched.getValue() == 1)
                {
                    Invoker spOT = new Invoker(ppv.getValue());
                    String id, desc, name, age, gender;
//                    spOT.invokeCheckHRESULT(ISpObjectToken_GetId, spOT, ppv);
//                    id = ppv.getValue().getWideString(0L);
                    spOT.invokeCheckHRESULT(ISpObjectToken_GetStringValue, spOT, Description, ppv);
                    desc = ppv.getValue().getWideString(0L);
//                    spOT.invokeCheckHRESULT(ISpObjectToken_OpenKey, spOT, Attributes_, ppv);
//                    try (Invoker spDK = new Invoker(ppv.getValue()))
//                    {
//                        spDK.invokeCheckHRESULT(ISpObjectToken_GetStringValue, spDK, Attributes_Name, ppv);
//                        name = ppv.getValue().getWideString(0L);
//                        spDK.invokeCheckHRESULT(ISpObjectToken_GetStringValue, spDK, Attributes_Age, ppv);
//                        age = ppv.getValue().getWideString(0L);
//                        spDK.invokeCheckHRESULT(ISpObjectToken_GetStringValue, spDK, Attributes_Gender, ppv);
//                        gender = ppv.getValue().getWideString(0L);
//                    }
                    this.voiceById.putIfAbsent(desc, spOT);
                    this.voices.add(desc);
                }
            }
        }
    }

    @Override
    public List<String> voices()
    {
        return Collections.unmodifiableList(this.voices);
    }

    @Override
    public CompletableFuture<Path> speak(String text, String voiceId, float volume, float speed)
    {
        return CompletableFuture.supplyAsync(() -> {
            Path path = this.dir.resolve(TtsProviderUtil.newRequestName() + ".wav");
            Invoker spV = this.synth;
            if (spV != null) try (Invoker spS = new Invoker(CLSID_SpStream, IID_ISpStream))
            {
                try
                {
                    spS.invokeCheckHRESULT(ISpStream_BindToFile, spS,
                        new WString(path.toString()),
                        0x3/*CREATE_ALWAYS*/,
                        SPDFID_WaveFormatEx,
                        WAVEFORMATEX_22kHz16BitMono,
                        0L/*don't care about events*/);
                    Invoker spOT = this.voiceById.get(voiceId);
                    if (spOT != null)
                    {
                        spV.invokeCheckHRESULT(ISpVoice_SetVoice, spV, spOT);
                    }
                    spV.invokeCheckHRESULT(ISpVoice_SetVolume, spV, volume(volume)/*0 through 100*/);
                    spV.invokeCheckHRESULT(ISpVoice_SetRate, spV, speed(speed)/*-10 through 10*/);
                    spV.invokeCheckHRESULT(ISpVoice_SetOutput, spV, spS, 1);
                    spV.invokeCheckHRESULT(ISpVoice_Speak, spV, new WString(text), 0x0010/*no xml*/, new IntByReference()/*returns stream number; unused, but shouldn't be null*/);
                }
                finally
                {
                    spS.invokeCheckHRESULT(ISpStream_Close, spS);
                }
            }
            finally
            {
                spV.invokeCheckHRESULT(ISpVoice_SetOutput, spV, null, 1);
            }
            return path;
        }, this.executor);
    }
    private static int speed(float speed)
    {
        return Math.max(-10, Math.min(Math.round(speed * 20 - 10), 10));
    }
    private static short volume(float volume)
    {
        return (short)(Math.max(0, Math.min(Math.round(volume * 100), 100)) & 0x0000_FFFF);
    }

    @Override
    public void close()
    {
        this.executor.submit(this::releaseVoices);
        this.executor.shutdown();
        try
        {
            if (this.executor.awaitTermination(10_000L, TimeUnit.MILLISECONDS))
                return;
        }
        catch (Exception ex)
        {
        }
        this.executor.shutdownNow();
    }
    private void releaseVoices()
    {
        this.voiceById.values().forEach(Invoker::close);
    }

    private class TtsThread extends Thread
    {
        TtsThread(Runnable target)
        {
            super(target);
        }
        @Override
        public void run()
        {
            COMUtils.checkRC(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED));
            try (Invoker spV = new Invoker(CLSID_SpVoice, IID_ISpVoice))
            {
                WinSapiTtsProvider.this.synth = spV;
                super.run();
            }
            finally
            {
                WinSapiTtsProvider.this.synth = null;
                Ole32.INSTANCE.CoUninitialize();
            }
        }
    }

    private static final CLSID
        CLSID_SpObjectTokenCategory = new CLSID("{A910187F-0C7A-45AC-92CC-59EDAFB77B53}"),
        CLSID_SpStream = new CLSID("{715D9C59-4442-11D2-9605-00C04F8EE628}"),
        CLSID_SpVoice = new CLSID("{96749377-3391-11D2-9EE3-00C04F797396}");
    private static final IID
        IID_ISpObjectTokenCategory = new IID("{2D3D3845-39AF-4850-BBF9-40B49780011D}"),
        IID_ISpStream = new IID("{12E3CCA9-7518-44C5-A5E7-BA5A79CB929E}"),
        IID_ISpVoice = new IID("{6C44DF74-72B9-4992-A1EC-EF996E0422D4}");
    private static final int
        IEnumSpObjectTokens_Next = 3,
        ISpObjectTokenCategory_SetId = 15,
        ISpObjectTokenCategory_EnumTokens = 18,
        ISpObjectToken_GetStringValue = 6,
        ISpObjectToken_OpenKey = 9,
        ISpObjectToken_GetId = 16,
        ISpStream_BindToFile = 17,
        ISpStream_Close = 18,
        ISpVoice_SetOutput = 13,
        ISpVoice_SetVoice = 18,
        ISpVoice_Speak = 20,
        ISpVoice_SetRate = 28,
        ISpVoice_SetVolume = 30;
    private static final WString
        VOICES_CATEGORY = new WString("HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Speech\\Voices"),
        Description = new WString(""),
        Attributes_ = new WString("Attributes"),
        Attributes_Name = new WString("Name"),
        Attributes_Age = new WString("Age"),
        Attributes_Gender = new WString("Gender");
    private static final GUID.ByReference
        SPDFID_WaveFormatEx = new GUID.ByReference(new GUID("{c31adbae-527f-4ff5-a230-f62bb61ff70c}"));
    private static final WaveFormatEx
        WAVEFORMATEX_22kHz16BitMono = SpConvertStreamFormatEnum(ESpFormat.SPSF_22kHz16BitMono);

    private static class Invoker extends Unknown implements Closeable
    {
        static Pointer create(CLSID clsid, IID iid)
        {
            PointerByReference ppv = new PointerByReference();
            COMUtils.checkRC(Ole32.INSTANCE.CoCreateInstance(clsid, null, WTypes.CLSCTX_ALL, iid, ppv));
            return ppv.getValue();
        }
        Invoker(CLSID clsid, IID iid)
        { this(create(clsid, iid)); }
        Invoker(Pointer pv)
        {
            super(pv);
        }
        void invokeCheckHRESULT(int vtidx, Object... args)
        {
            COMUtils.checkRC(this.invokeHRESULT(vtidx, args));
        }
        HRESULT invokeHRESULT(int vtidx, Object... args)
        {
            Pointer ptr = this.getPointer(),
                    vtptr = ptr.getPointer(0);
            Function func = Function.getFunction(vtptr.getPointer(Native.POINTER_SIZE * vtidx), Function.C_CONVENTION/*ALT_CONVENTION*/);
            if (args[0] != this)
                throw new IllegalArgumentException("args[0] != this");
            args[0] = ptr;
            for (int i = 1; i < args.length; i++)
                if (args[i] instanceof Invoker)
                    args[i] = ((Invoker)args[i]).getPointer();
            return (HRESULT)func.invoke(HRESULT.class, args);
        }
        @Override
        public void close()
        {
            this.Release();
        }
    }

    public interface ESpFormat
    {
        int SPSF_Default    = -1,
            SPSF_NoAssignedFormat   = 0,
            SPSF_Text   = ( SPSF_NoAssignedFormat + 1 ) ,
            SPSF_NonStandardFormat  = ( SPSF_Text + 1 ) ,
            SPSF_ExtendedAudioFormat    = ( SPSF_NonStandardFormat + 1 ) ,
            SPSF_8kHz8BitMono   = ( SPSF_ExtendedAudioFormat + 1 ) ,
            SPSF_8kHz8BitStereo = ( SPSF_8kHz8BitMono + 1 ) ,
            SPSF_8kHz16BitMono  = ( SPSF_8kHz8BitStereo + 1 ) ,
            SPSF_8kHz16BitStereo    = ( SPSF_8kHz16BitMono + 1 ) ,
            SPSF_11kHz8BitMono  = ( SPSF_8kHz16BitStereo + 1 ) ,
            SPSF_11kHz8BitStereo    = ( SPSF_11kHz8BitMono + 1 ) ,
            SPSF_11kHz16BitMono = ( SPSF_11kHz8BitStereo + 1 ) ,
            SPSF_11kHz16BitStereo   = ( SPSF_11kHz16BitMono + 1 ) ,
            SPSF_12kHz8BitMono  = ( SPSF_11kHz16BitStereo + 1 ) ,
            SPSF_12kHz8BitStereo    = ( SPSF_12kHz8BitMono + 1 ) ,
            SPSF_12kHz16BitMono = ( SPSF_12kHz8BitStereo + 1 ) ,
            SPSF_12kHz16BitStereo   = ( SPSF_12kHz16BitMono + 1 ) ,
            SPSF_16kHz8BitMono  = ( SPSF_12kHz16BitStereo + 1 ) ,
            SPSF_16kHz8BitStereo    = ( SPSF_16kHz8BitMono + 1 ) ,
            SPSF_16kHz16BitMono = ( SPSF_16kHz8BitStereo + 1 ) ,
            SPSF_16kHz16BitStereo   = ( SPSF_16kHz16BitMono + 1 ) ,
            SPSF_22kHz8BitMono  = ( SPSF_16kHz16BitStereo + 1 ) ,
            SPSF_22kHz8BitStereo    = ( SPSF_22kHz8BitMono + 1 ) ,
            SPSF_22kHz16BitMono = ( SPSF_22kHz8BitStereo + 1 ) ,
            SPSF_22kHz16BitStereo   = ( SPSF_22kHz16BitMono + 1 ) ,
            SPSF_24kHz8BitMono  = ( SPSF_22kHz16BitStereo + 1 ) ,
            SPSF_24kHz8BitStereo    = ( SPSF_24kHz8BitMono + 1 ) ,
            SPSF_24kHz16BitMono = ( SPSF_24kHz8BitStereo + 1 ) ,
            SPSF_24kHz16BitStereo   = ( SPSF_24kHz16BitMono + 1 ) ,
            SPSF_32kHz8BitMono  = ( SPSF_24kHz16BitStereo + 1 ) ,
            SPSF_32kHz8BitStereo    = ( SPSF_32kHz8BitMono + 1 ) ,
            SPSF_32kHz16BitMono = ( SPSF_32kHz8BitStereo + 1 ) ,
            SPSF_32kHz16BitStereo   = ( SPSF_32kHz16BitMono + 1 ) ,
            SPSF_44kHz8BitMono  = ( SPSF_32kHz16BitStereo + 1 ) ,
            SPSF_44kHz8BitStereo    = ( SPSF_44kHz8BitMono + 1 ) ,
            SPSF_44kHz16BitMono = ( SPSF_44kHz8BitStereo + 1 ) ,
            SPSF_44kHz16BitStereo   = ( SPSF_44kHz16BitMono + 1 ) ,
            SPSF_48kHz8BitMono  = ( SPSF_44kHz16BitStereo + 1 ) ,
            SPSF_48kHz8BitStereo    = ( SPSF_48kHz8BitMono + 1 ) ,
            SPSF_48kHz16BitMono = ( SPSF_48kHz8BitStereo + 1 ) ,
            SPSF_48kHz16BitStereo   = ( SPSF_48kHz16BitMono + 1 ) ,
            SPSF_TrueSpeech_8kHz1BitMono    = ( SPSF_48kHz16BitStereo + 1 ) ,
            SPSF_CCITT_ALaw_8kHzMono    = ( SPSF_TrueSpeech_8kHz1BitMono + 1 ) ,
            SPSF_CCITT_ALaw_8kHzStereo  = ( SPSF_CCITT_ALaw_8kHzMono + 1 ) ,
            SPSF_CCITT_ALaw_11kHzMono   = ( SPSF_CCITT_ALaw_8kHzStereo + 1 ) ,
            SPSF_CCITT_ALaw_11kHzStereo = ( SPSF_CCITT_ALaw_11kHzMono + 1 ) ,
            SPSF_CCITT_ALaw_22kHzMono   = ( SPSF_CCITT_ALaw_11kHzStereo + 1 ) ,
            SPSF_CCITT_ALaw_22kHzStereo = ( SPSF_CCITT_ALaw_22kHzMono + 1 ) ,
            SPSF_CCITT_ALaw_44kHzMono   = ( SPSF_CCITT_ALaw_22kHzStereo + 1 ) ,
            SPSF_CCITT_ALaw_44kHzStereo = ( SPSF_CCITT_ALaw_44kHzMono + 1 ) ,
            SPSF_CCITT_uLaw_8kHzMono    = ( SPSF_CCITT_ALaw_44kHzStereo + 1 ) ,
            SPSF_CCITT_uLaw_8kHzStereo  = ( SPSF_CCITT_uLaw_8kHzMono + 1 ) ,
            SPSF_CCITT_uLaw_11kHzMono   = ( SPSF_CCITT_uLaw_8kHzStereo + 1 ) ,
            SPSF_CCITT_uLaw_11kHzStereo = ( SPSF_CCITT_uLaw_11kHzMono + 1 ) ,
            SPSF_CCITT_uLaw_22kHzMono   = ( SPSF_CCITT_uLaw_11kHzStereo + 1 ) ,
            SPSF_CCITT_uLaw_22kHzStereo = ( SPSF_CCITT_uLaw_22kHzMono + 1 ) ,
            SPSF_CCITT_uLaw_44kHzMono   = ( SPSF_CCITT_uLaw_22kHzStereo + 1 ) ,
            SPSF_CCITT_uLaw_44kHzStereo = ( SPSF_CCITT_uLaw_44kHzMono + 1 ) ,
            SPSF_ADPCM_8kHzMono = ( SPSF_CCITT_uLaw_44kHzStereo + 1 ) ,
            SPSF_ADPCM_8kHzStereo   = ( SPSF_ADPCM_8kHzMono + 1 ) ,
            SPSF_ADPCM_11kHzMono    = ( SPSF_ADPCM_8kHzStereo + 1 ) ,
            SPSF_ADPCM_11kHzStereo  = ( SPSF_ADPCM_11kHzMono + 1 ) ,
            SPSF_ADPCM_22kHzMono    = ( SPSF_ADPCM_11kHzStereo + 1 ) ,
            SPSF_ADPCM_22kHzStereo  = ( SPSF_ADPCM_22kHzMono + 1 ) ,
            SPSF_ADPCM_44kHzMono    = ( SPSF_ADPCM_22kHzStereo + 1 ) ,
            SPSF_ADPCM_44kHzStereo  = ( SPSF_ADPCM_44kHzMono + 1 ) ,
            SPSF_GSM610_8kHzMono    = ( SPSF_ADPCM_44kHzStereo + 1 ) ,
            SPSF_GSM610_11kHzMono   = ( SPSF_GSM610_8kHzMono + 1 ) ,
            SPSF_GSM610_22kHzMono   = ( SPSF_GSM610_11kHzMono + 1 ) ,
            SPSF_GSM610_44kHzMono   = ( SPSF_GSM610_22kHzMono + 1 ) ,
            SPSF_NUM_FORMATS    = ( SPSF_GSM610_44kHzMono + 1 ) ;
    }

    public static class WaveFormatEx extends Structure
    {
        static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList("wFormatTag", "nChannels", "nSamplesPerSec", "nAvgBytesPerSec", "nBlockAlign", "wBitsPerSample", "cbSize", "_extra"));
        public static class ByReference extends WaveFormatEx implements Structure.ByReference
        {
            public ByReference()
            {
            }
            public ByReference(Pointer memory)
            {
                super(memory);
            }
        }
        public WaveFormatEx()
        {
            super();
        }
        public WaveFormatEx(Pointer memory)
        {
            super(memory);
            this.read();
        }
        public short wFormatTag;
        public short nChannels;
        public int nSamplesPerSec;
        public int nAvgBytesPerSec;
        public short nBlockAlign;
        public short wBitsPerSample;
        public short cbSize;
        public final byte[] _extra = new byte[32];
        @Override
        protected List<String> getFieldOrder()
        {
            return FIELDS;
        }
    }

    /**
     * um\sphelper.h
     */
    public static WaveFormatEx SpConvertStreamFormatEnum(int format)
    {
        WaveFormatEx pwfex = new WaveFormatEx();
        if (format >= ESpFormat.SPSF_8kHz8BitMono && format <= ESpFormat.SPSF_48kHz16BitStereo)
        {
            int index = format - ESpFormat.SPSF_8kHz8BitMono;
            boolean isStereo = (index & 0x1) != 0,
                    is16 = (index & 0x2) != 0;
            int khz = (index & 0x3c) >> 2,
                akhz[] = { 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 };
            pwfex.wFormatTag = /*(WAVE_FORMAT_PCM)*/0x0001;
            pwfex.nChannels = pwfex.nBlockAlign = isStereo ? (short)2 : (short)1;
            pwfex.nSamplesPerSec = akhz[khz < akhz.length ? khz : 0];
            pwfex.wBitsPerSample = 8;
            if (is16)
            {
                pwfex.wBitsPerSample *= 2;
                pwfex.nBlockAlign *= 2;
            }
            pwfex.nAvgBytesPerSec = pwfex.nSamplesPerSec * pwfex.nBlockAlign;
            pwfex.cbSize = 0;
        }
        else if (format == ESpFormat.SPSF_TrueSpeech_8kHz1BitMono)
        {
            pwfex.wFormatTag      = /*(WAVE_FORMAT_DSPGROUP_TRUESPEECH)*/0x0022;
            pwfex.nChannels       = 1;
            pwfex.nSamplesPerSec  = 8000;
            pwfex.nAvgBytesPerSec = 1067;
            pwfex.nBlockAlign     = 32;
            pwfex.wBitsPerSample  = 1;
            pwfex.cbSize          = 32;
            pwfex._extra[0]       = 1;
            pwfex._extra[2]       = (byte)0xF0;
        }
        else if (format >= ESpFormat.SPSF_CCITT_ALaw_8kHzMono && format <= ESpFormat.SPSF_CCITT_ALaw_44kHzStereo)
        {
            int index = format - ESpFormat.SPSF_CCITT_ALaw_8kHzMono,
                khz = index / 2,
                akhz[] = { 8000, 11025, 22050, 44100 };
            boolean isStereo    = (index & 0x1) != 0;
            pwfex.wFormatTag      = /*(WAVE_FORMAT_ALAW)*/0x0006;
            pwfex.nChannels       = pwfex.nBlockAlign = isStereo ? (short)2 : (short)1;
            pwfex.nSamplesPerSec  = akhz[khz < akhz.length ? khz : 0];
            pwfex.wBitsPerSample  = 8;
            pwfex.nAvgBytesPerSec = pwfex.nSamplesPerSec * pwfex.nBlockAlign;
            pwfex.cbSize          = 0;
        }
        else if (format >= ESpFormat.SPSF_CCITT_uLaw_8kHzMono && format <= ESpFormat.SPSF_CCITT_uLaw_44kHzStereo)
        {
            int index = format - ESpFormat.SPSF_CCITT_uLaw_8kHzMono,
                khz = index / 2,
                akhz[] = { 8000, 11025, 22050, 44100 };
            boolean isStereo = (index & 0x1) != 0;
            pwfex.wFormatTag = /* (WAVE_FORMAT_MULAW) */0x0007;
            pwfex.nChannels = pwfex.nBlockAlign = isStereo ? (short) 2 : (short) 1;
            pwfex.nSamplesPerSec = akhz[khz < akhz.length ? khz : 0];
            pwfex.wBitsPerSample = 8;
            pwfex.nAvgBytesPerSec = pwfex.nSamplesPerSec * pwfex.nBlockAlign;
            pwfex.cbSize = 0;
        }
        else if (format >= ESpFormat.SPSF_ADPCM_8kHzMono && format <= ESpFormat.SPSF_ADPCM_44kHzStereo)
        {
            // --- Some of these values seem odd. We used what the codec told us.
            int akhz[] = { 8000, 11025, 22050, 44100 },
                BytesPerSec[] = { 4096, 8192, 5644, 11289, 11155, 22311, 22179, 44359 };
            short BlockAlign[] = { 256, 256, 512, 1024 };
            byte Extra811[] = { (byte) 0xF4, 0x01, 0x07, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, (byte) 0xFF,
                    0x00, 0x00, 0x00, 0x00, (byte) 0xC0, 0x00, 0x40, 0x00, (byte) 0xF0, 0x00, 0x00, 0x00, (byte) 0xCC,
                    0x01, 0x30, (byte) 0xFF, (byte) 0x88, 0x01, 0x18, (byte) 0xFF },
                 Extra22[] = { (byte) 0xF4, 0x03, 0x07, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, (byte) 0xFF,
                            0x00, 0x00, 0x00, 0x00, (byte) 0xC0, 0x00, 0x40, 0x00, (byte) 0xF0, 0x00, 0x00, 0x00,
                            (byte) 0xCC, 0x01, 0x30, (byte) 0xFF, (byte) 0x88, 0x01, 0x18, (byte) 0xFF },
                 Extra44[] = { (byte) 0xF4, 0x07, 0x07, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, (byte) 0xFF,
                            0x00, 0x00, 0x00, 0x00, (byte) 0xC0, 0x00, 0x40, 0x00, (byte) 0xF0, 0x00, 0x00, 0x00,
                            (byte) 0xCC, 0x01, 0x30, (byte) 0xFF, (byte) 0x88, 0x01, 0x18, (byte) 0xFF },
                 Extra[][] = { Extra811, Extra811, Extra22, Extra44 };
            int dwIndex = format - ESpFormat.SPSF_ADPCM_8kHzMono, dwKHZ = dwIndex / 2;
            boolean bIsStereo = (dwIndex & 0x1) != 0;
            pwfex.wFormatTag = /* (WAVE_FORMAT_ADPCM) */0x0002;
            pwfex.nChannels = bIsStereo ? (short) 2 : (short) 1;
            pwfex.nSamplesPerSec = akhz[dwKHZ < akhz.length ? dwKHZ : 0];
            pwfex.nAvgBytesPerSec = BytesPerSec[dwKHZ < BytesPerSec.length ? dwKHZ : 0];
            pwfex.nBlockAlign = (short) (BlockAlign[dwKHZ < BlockAlign.length ? dwKHZ : 0] * pwfex.nChannels);
            pwfex.wBitsPerSample = 4;
            pwfex.cbSize = 32;
            System.arraycopy(Extra[dwKHZ < Extra.length ? dwKHZ : 0], 0, pwfex._extra, 0, 32);
        }
        else if (format >= ESpFormat.SPSF_GSM610_8kHzMono && format <= ESpFormat.SPSF_GSM610_44kHzMono)
        {
            // --- Some of these values seem odd. We used what the codec told us.
            int akhz[] = { 8000, 11025, 22050, 44100 },
                BytesPerSec[] = { 1625, 2239, 4478, 8957 },
                index = format - ESpFormat.SPSF_GSM610_8kHzMono;
            pwfex.wFormatTag = /* (WAVE_FORMAT_GSM610) */0x0031;
            pwfex.nChannels = 1;
            pwfex.nSamplesPerSec = akhz[index < akhz.length ? index : 0];
            pwfex.nAvgBytesPerSec = BytesPerSec[index < BytesPerSec.length ? index : 0];
            pwfex.nBlockAlign = 65;
            pwfex.wBitsPerSample = 0;
            pwfex.cbSize = 2;
            pwfex._extra[0] = 0x40;
            pwfex._extra[1] = 0x01;
        }
        return pwfex;
    }

}
