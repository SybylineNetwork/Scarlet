package net.sybyline.scarlet.ext;

import io.github.vrchatapi.model.FileAnalysis;
import io.github.vrchatapi.model.ModelFile;

import net.sybyline.scarlet.util.VersionedFile;

public class AvatarBundleInfo
{

    public AvatarBundleInfo(VersionedFile id, ModelFile file, FileAnalysis analysis)
    {
        this.id = id;
        this.file = file;
        this.analysis = analysis;
    }

    public VersionedFile id;
    public ModelFile file;
    public FileAnalysis analysis;

}
