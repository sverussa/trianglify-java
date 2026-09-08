package com.zyn.trianglify.gui;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;

import java.util.List;
import java.util.prefs.Preferences;

/**
 * Os temas de cor do AtlantaFX disponíveis na janela, com a preferência do
 * usuário guardada entre execuções.
 */
public enum AppTheme {

    PRIMER_LIGHT("Primer Claro", new PrimerLight()),
    PRIMER_DARK("Primer Escuro", new PrimerDark()),
    NORD_LIGHT("Nord Claro", new NordLight()),
    NORD_DARK("Nord Escuro", new NordDark()),
    CUPERTINO_LIGHT("Cupertino Claro", new CupertinoLight()),
    CUPERTINO_DARK("Cupertino Escuro", new CupertinoDark()),
    DRACULA("Dracula", new Dracula());

    private static final Preferences PREFS =
            Preferences.userRoot().node("com/zyn/trianglify/gui");
    private static final String PREF_KEY = "theme";
    private static final AppTheme FALLBACK = PRIMER_DARK;

    private final String label;
    private final Theme theme;

    AppTheme(String label, Theme theme) {
        this.label = label;
        this.theme = theme;
    }

    public boolean isDark() {
        return theme.isDarkMode();
    }

    /** Aplica o tema à aplicação inteira (todas as janelas abertas). */
    public void apply() {
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        PREFS.put(PREF_KEY, name());
    }

    /** Tema escolhido da última vez, ou o padrão na primeira execução. */
    public static AppTheme saved() {
        String name = PREFS.get(PREF_KEY, FALLBACK.name());
        return List.of(values()).stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElse(FALLBACK);
    }

    @Override
    public String toString() {
        return label;
    }
}
