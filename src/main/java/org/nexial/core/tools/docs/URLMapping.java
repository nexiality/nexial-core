package org.nexial.core.tools.docs;

public class URLMapping {
    private String miniDocUrl;
    private String miniDocFile;
    private String fullDocUrl;

    public URLMapping(String miniDocUrl, String miniDocFile, String fullDocUrl) {
        this.miniDocUrl  = miniDocUrl;
        this.miniDocFile = miniDocFile;
        this.fullDocUrl  = fullDocUrl;
    }


    @Override
    public String toString() {
        return "URLMapping{" +
               "miniDocUrl='" + miniDocUrl + '\'' +
               ", miniDocFile='" + miniDocFile + '\'' +
               ", fullDocUrl='" + fullDocUrl + '\'' +
               '}';
    }

    public String getMiniDocUrl() {
        return miniDocUrl;
    }

    public void setMiniDocUrl(String miniDocUrl) {
        this.miniDocUrl = miniDocUrl;
    }

    public String getMiniDocFile() {
        return miniDocFile;
    }

    public void setMiniDocFile(String miniDocFile) {
        this.miniDocFile = miniDocFile;
    }

    public String getFullDocUrl() {
        return fullDocUrl;
    }

    public void setFullDocUrl(String fullDocUrl) {
        this.fullDocUrl = fullDocUrl;
    }

}
