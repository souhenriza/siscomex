package com.souhenriza;

public enum Environment {
    PRODUCAO("https://portalunico.siscomex.gov.br/"),
    VALIDACAO("https://val.portalunico.siscomex.gov.br/"),
    HOMOLOGACAO("https://hom.pucomex.serpro.gov.br/");

    private final String url;
    Environment(String url) {

        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
