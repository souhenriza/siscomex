package com.souhenriza.auth;

import com.souhenriza.auth.error.SiscomexException;
import com.souhenriza.auth.session.Tokens;

public interface Authenticator {

    Tokens authenticate() throws SiscomexException;
}
