//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.scanner;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.tokens.Token;

public interface Scanner {
    boolean checkToken(Token.ID... var1);

    Token peekToken();

    Token getToken();
}
