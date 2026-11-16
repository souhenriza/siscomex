package com.souhenriza.auth.credential;

import com.souhenriza.auth.RoleType;

import javax.net.ssl.SSLContext;
import java.util.Map;

public interface Credentials {
    SSLContext sslContext();
    RoleType roleType();
    Map<String, String> authHeaders();
}
