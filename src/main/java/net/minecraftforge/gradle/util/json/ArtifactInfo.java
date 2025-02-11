package net.minecraftforge.gradle.util.json;

public class ArtifactInfo {
    public final String packageName;
    public final String artificialName;
    public final String version;
    public final String extension;

    public ArtifactInfo(String pkg, String name, String ver, String ext) {
        this.packageName = pkg;
        this.artificialName = name;
        this.version = ver;
        this.extension = ext;
    }

    public ArtifactInfo(String pkg, String name, String ver) {
        this(pkg, name, ver, null);
    }

    public static ArtifactInfo fromString(String s) {
        if (s.indexOf('@') == -1) {
            String[] ver = s.split(":");
            if (ver.length == 3) {
                return new ArtifactInfo(ver[0], ver[1], ver[2]);
            } else if (ver.length == 4){
                return new ArtifactInfo(ver[0], ver[1], ver[2], ver[3]);
            } else return null;
        } else {
            String[] ver = s.split(":");
            if (ver.length == 3) {
                return new ArtifactInfo(ver[0], ver[1], ver[2].substring(0, ver[2].indexOf('@')), ver[2].substring(ver[2].indexOf('@') + 1));
            } else return null;
        }
    }
}
