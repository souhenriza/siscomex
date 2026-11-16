package com.souhenriza;

import com.souhenriza.auth.RoleType;
import com.souhenriza.auth.credential.A1Certificate;
import com.souhenriza.duimp.DuimpClient;
import com.souhenriza.duimp.NumeroDuimp;
import com.souhenriza.duimp.VersaoDuimp;
import com.souhenriza.duimp.model.DuimpDados;

import java.nio.file.Path;


public class Main {
    public static void main(String[] args) {
        A1Certificate certificate = A1Certificate.from(Path.of("src/test/resources/certificates/ecpf.p12"), "senha".toCharArray(), RoleType.IMPEXP);

        Siscomex siscomex = new Siscomex(Environment.VALIDACAO, certificate);

        DuimpClient client = new DuimpClient(siscomex);

        DuimpDados duimpDados = client.consultarDados(NumeroDuimp.of("26BR00000000000"), VersaoDuimp.of(1));


        System.out.printf(duimpDados.raw().asString());
    }
}
