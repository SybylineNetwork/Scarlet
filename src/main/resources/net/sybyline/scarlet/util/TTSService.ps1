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
                foreach (InstalledVoice voice in synth.GetInstalledVoices())
                {
                    Console.WriteLine("@"+voice.VoiceInfo.Name);
                }
                string dir = Console.ReadLine();
                bool speakToDefaultAudioDevice = false;
                for (string line; (line = Console.ReadLine()) != null;)
                {
                    if ("stop".Equals(line))
                    {
                        return;
                    }
                    else if (line.StartsWith("!"))
                    {
                        speakToDefaultAudioDevice = bool.Parse(line.Substring(1));
                    }
                    else if (line.StartsWith("@"))
                    {
                        synth.SelectVoice(line.Substring(1));
                    }
                    else if (line.StartsWith("+"))
                    {
	                    int semicolon = line.IndexOf(';');
                    	if (speakToDefaultAudioDevice)
                    	{
	                        synth.SetOutputToDefaultAudioDevice();
	                        synth.Speak(new Prompt(line.Substring(semicolon + 1)));
                    	}
                    	else
                    	{
	                        string path = dir+"\\tts_"+line.Substring(0, semicolon)+"_audio.wav";
	                        synth.SetOutputToWaveFile(path, safi);
	                        synth.Speak(new Prompt(line.Substring(semicolon + 1)));
	                        Console.WriteLine("+"+path);
                        }
                    }
                    else if (line.StartsWith("="))
                    {
	                    int semicolon = line.IndexOf(';');
                    	if (speakToDefaultAudioDevice)
                    	{
	                        synth.SetOutputToDefaultAudioDevice();
	                        synth.SpeakSsml(line.Substring(semicolon + 1));
                    	}
                    	else
                    	{
	                        string path = dir+"\\tts_"+line.Substring(0, semicolon)+"_audio.wav";
	                        synth.SetOutputToWaveFile(path, safi);
	                        synth.SpeakSsml(line.Substring(semicolon + 1));
	                        Console.WriteLine("+"+path);
                        }
                    }
                    else if (line.StartsWith("["))
                    {
                        synth.AddLexicon(new Uri(line.Substring(1)), "application/pls+xml");
                    }
                    else if (line.StartsWith("]"))
                    {
                        synth.RemoveLexicon(new Uri(line.Substring(1)));
                    }
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
