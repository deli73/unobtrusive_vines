package xyz.sunrose.unobtrusive_vines;

import net.fabricmc.api.ModInitializer;
import net.minecraft.state.property.BooleanProperty;

public class UnobtrusiveVines implements ModInitializer {
    public static final BooleanProperty CAN_GROW = BooleanProperty.of("can_grow");
    @Override
    public void onInitialize() {

    }
}
