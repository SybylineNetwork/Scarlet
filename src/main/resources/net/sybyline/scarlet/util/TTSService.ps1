# yee

$assemblies = @("System.Speech")

$code = @"
using System;
using System.Speech;
using System.Speech.AudioFormat;
using System.Speech.Synthesis;

namespace SybylineNetwork
{
    public class TTSService
    {
        public static void Loop()
        {
            SpeechAudioFormatInfo safi = new SpeechAudioFormatInfo(48000, AudioBitsPerSample.Sixteen, AudioChannel.Stereo);
            using (SpeechSynthesizer synth = new SpeechSynthesizer())
            {
                string dir = Console.ReadLine();
                string line;
                for (uint idx = 0; (line = Console.ReadLine()) != null; idx++)
                {
                    if ("stop".Equals(line))
                    {
                        return;
                    }
                    string path = dir+"\\tts_"+idx+"_audio.wav";
                    synth.SetOutputToWaveFile(path, safi);
                    synth.Speak(new Prompt(line));
                    Console.WriteLine(path);
                }
            }
        }
    }
}
"@

foreach ($assembly in $assemblies)
{
    Add-Type -AssemblyName $assembly
}

Add-Type -TypeDefinition $code -Language CSharp	-ReferencedAssemblies $assemblies

iex "[SybylineNetwork.TTSService]::Loop()"
