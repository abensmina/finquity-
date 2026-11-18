package com.finquity.ui;

import com.finquity.ui.view.Screen;

// Ce que les écrans peuvent demander à l'application : changer d'écran.
// FinquityApp réalise cette interface ; les écrans ne connaissent que l'interface.
public interface Navigator {

    void show(Screen screen);

    ApiClient api();
}
