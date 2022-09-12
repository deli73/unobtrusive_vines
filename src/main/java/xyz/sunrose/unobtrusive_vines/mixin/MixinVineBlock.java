package xyz.sunrose.unobtrusive_vines.mixin;


import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.sunrose.unobtrusive_vines.UnobtrusiveVines;

import java.util.Iterator;
import java.util.Map;

@Mixin(VineBlock.class)
public abstract class MixinVineBlock extends Block implements Fertilizable {
    @Shadow
    public static BooleanProperty getFacingProperty(Direction direction) {
        return null;
    }

    @Shadow protected abstract boolean canGrowAt(BlockView world, BlockPos pos);

    @Shadow
    public static boolean shouldConnectTo(BlockView world, BlockPos pos, Direction direction) {
        return false;
    }

    @Shadow protected abstract boolean shouldHaveSide(BlockView world, BlockPos pos, Direction side);

    @Shadow @Final public static BooleanProperty UP;

    @Shadow protected abstract boolean hasHorizontalSide(BlockState state);

    @Shadow protected abstract BlockState getGrownState(BlockState above, BlockState state, Random random);


    @Shadow @Final public static Map<Direction, BooleanProperty> FACING_PROPERTIES;
    // == PROPERTIES AND INJECTIONS ==
    private static final BooleanProperty CAN_GROW = UnobtrusiveVines.CAN_GROW;
    private static final Direction[] DIRS = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

    public MixinVineBlock(Settings settings) {
        super(settings);
    }

    @Inject(method = "<init>(Lnet/minecraft/block/AbstractBlock$Settings;)V", at = @At(value = "TAIL"))
    private void injectConstructor(Settings settings, CallbackInfo ci){
        this.setDefaultState(this.getDefaultState().with(CAN_GROW, true));
    }

    @Inject(method = "appendProperties", at = @At(value = "TAIL"))
    private void addMoreProperties(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci){
        builder.add(CAN_GROW);
    }


    // == GROWTH ==

    private boolean canGrowDown(BlockView world, BlockPos pos, BlockState state) {
        BlockState belowState = world.getBlockState(pos.down());
        if (!belowState.isOf(Blocks.VINE)) {
            return belowState.isAir();
        }
        for (Direction side : DIRS){ //if theres a free spot to grow from the current sides to down below, then growable
            if (state.get(FACING_PROPERTIES.get(side)) && !belowState.get(FACING_PROPERTIES.get(side))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFertilizable(BlockView world, BlockPos pos, BlockState state, boolean isClient) {
        return canGrowDown(world, pos, state);
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return canGrowDown(world, pos, state);
    }

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        if (pos.getY() > world.getBottomY()) {
            BlockPos below = pos.down();
            BlockState belowState = world.getBlockState(below);
            if (belowState.isAir() || belowState.isOf(this)) {
                BlockState nonAirBelowState = belowState.isAir() ? this.getDefaultState() : belowState;
                BlockState grownState = this.getGrownState(state, nonAirBelowState, random).with(CAN_GROW, true);
                if (nonAirBelowState != grownState && this.hasHorizontalSide(grownState)) {
                    world.setBlockState(below, grownState, 2);
                    world.setBlockState(pos, state.with(CAN_GROW, true));
                }
            }
        }
    }

    @Inject(method = "randomTick", at = @At(value = "HEAD"), cancellable = true)
    private void overrideVineGrowth(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci){
        // grow only downwards
        if (random.nextInt(4) == 0 && state.get(CAN_GROW)) {
            if (pos.getY() > world.getBottomY()) {
                BlockPos below = pos.down();
                BlockState belowState = world.getBlockState(below);
                if (belowState.isAir() || belowState.isOf(this) && belowState.get(CAN_GROW)) {
                    BlockState nonAirBelowState = belowState.isAir() ? this.getDefaultState() : belowState;
                    BlockState grownState = this.getGrownState(state, nonAirBelowState, random);
                    if (nonAirBelowState != grownState && this.hasHorizontalSide(grownState)) {
                        world.setBlockState(below, grownState, 2);
                    }
                }
            }
        }
        ci.cancel();
    }

}
