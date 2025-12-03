package evo.developers.ru.macios.effects;

public enum Material {
    TITLEBAR(0),
    SELECTION(1),
    MENU(2),
    POPOVER(3),
    SIDEBAR(4),
    HEADER_VIEW(5),
    SHEET(6),
    WINDOW_BACKGROUND(7),
    HUD(8),
    FULLSCREEN_UI(9),
    TOOLTIP(10),
    CONTENT_BACKGROUND(11),
    UNDER_WINDOW(12),
    UNDER_PAGE(13),
    ULTRA_THIN(1),
    THIN(2),
    MEDIUM(3),
    THICK(4),
    ULTRA_THICK(8);
    
    public final long value;
    Material(long value) { this.value = value; }
}