package xyz.sunrose.unobtrusive_vines.mixin;


import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.VineBlock;
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


    // == PROPERTIES AND INJECTIONS ==
    private static final BooleanProperty CAN_GROW = UnobtrusiveVines.CAN_GROW;

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

    @Override
    public boolean isFertilizable(BlockView world, BlockPos pos, BlockState state, boolean isClient) {
        return canGrowAt(world, pos);
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        // vanilla randomTick of vine block but better notated, with chances altered
        for (int i = 0; i < 2; i++) { // run twice every call instead of ~once every four calls
            Direction randomDir = Direction.random(random); // pick a random direction
            //if the direction is horizontal and we don't have a vine on that side of the block (??)
            if (randomDir.getAxis().isHorizontal() && !(Boolean)state.get(getFacingProperty(randomDir))) {
                // if we can grow here
                if (this.canGrowAt(world, pos)) {
                    // get the position and state of the offset in the random direction
                    BlockPos offsetPos = pos.offset(randomDir);
                    BlockState offsetState = world.getBlockState(offsetPos);
                    // if the offset position is air///
                    if (offsetState.isAir()) {
                        // pick the directions either side
                        Direction rotatedCW = randomDir.rotateYClockwise();
                        Direction rotatedCCW = randomDir.rotateYCounterclockwise();
                        boolean isOnCWFace = state.get(getFacingProperty(rotatedCW));
                        boolean isOnCCWFace = state.get(getFacingProperty(rotatedCCW));
                        BlockPos offsetPosCW = offsetPos.offset(rotatedCW);
                        BlockPos offsetPosCCW = offsetPos.offset(rotatedCCW);
                        // if there's a vine on the new side and it'd connect with the new offset, grow to there
                        if (isOnCWFace && shouldConnectTo(world, offsetPosCW, rotatedCW)) {
                            world.setBlockState(offsetPos, this.getDefaultState().with(getFacingProperty(rotatedCW), true), 2);
                        } else if (isOnCCWFace && shouldConnectTo(world, offsetPosCCW, rotatedCCW)) {
                            world.setBlockState(offsetPos, this.getDefaultState().with(getFacingProperty(rotatedCCW), true), 2);
                        } else { //otherwise, try the other direction
                            Direction reverseDir = randomDir.getOpposite();
                            if (isOnCWFace && world.isAir(offsetPosCW) && shouldConnectTo(world, pos.offset(rotatedCW), reverseDir)) {
                                world.setBlockState(offsetPosCW, this.getDefaultState().with(getFacingProperty(reverseDir), true), 2);
                            } else if (isOnCCWFace && world.isAir(offsetPosCCW) && shouldConnectTo(world, pos.offset(rotatedCCW), reverseDir)) {
                                world.setBlockState(offsetPosCCW, this.getDefaultState().with(getFacingProperty(reverseDir), true), 2);
                            } else if ((double)random.nextFloat() < 0.05D && shouldConnectTo(world, offsetPos.up(), Direction.UP)) {
                                // finally, if all else fails, 5% chance to grow upwards (??)
                                world.setBlockState(offsetPos, this.getDefaultState().with(UP, true), 2);
                            }
                        }
                    } else if (shouldConnectTo(world, offsetPos, randomDir)) { //if the offset position isn't air, just grow that way
                        world.setBlockState(pos, state.with(getFacingProperty(randomDir), true), 2);
                    }

                }
            } else { //if the direction isn't horizontal or the current vine doesn't have a side on the random direction
                if (randomDir == Direction.UP && pos.getY() < world.getTopY() - 1) { //if the direction is up and there's space in the world
                    if (this.shouldHaveSide(world, pos, randomDir)) { // grow to top side of current block if possible/needed
                        world.setBlockState(pos, state.with(UP, true), 2);
                        return;
                    }


                    BlockPos abovePos = pos.up();

                    if (world.isAir(abovePos)) { //if there's air above,
                        if (!this.canGrowAt(world, pos)) {
                            return;
                        }

                        //and we can grow here, then,

                        BlockState currentState = state;
                        Iterator<Direction> horiontalDirections = Direction.Type.HORIZONTAL.iterator();
                        Direction direction;

                        while(true) { //check all horizontal directions and try to grow upwards, until out of connections or a random bool returns false
                            do {
                                if (!horiontalDirections.hasNext()) {
                                    if (this.hasHorizontalSide(currentState)) {
                                        world.setBlockState(abovePos, currentState, 2);
                                    }

                                    return;
                                }

                                direction = horiontalDirections.next();
                            } while(!random.nextBoolean() && shouldConnectTo(world, abovePos.offset(direction), direction));

                            currentState = currentState.with(getFacingProperty(direction), false); //don't face that way anymore ??
                        }
                    }
                }

                if (pos.getY() > world.getBottomY()) {//if we're above the bottom of the world, grow downwards
                    BlockPos belowPos = pos.down();
                    BlockState belowState = world.getBlockState(belowPos);
                    if (belowState.isAir() || belowState.isOf(this)) {
                        BlockState nonAirBelowState = belowState.isAir() ? this.getDefaultState() : belowState;
                        BlockState grownState = this.getGrownState(state, nonAirBelowState, random);
                        if (nonAirBelowState != grownState && this.hasHorizontalSide(grownState)) {
                            world.setBlockState(belowPos, grownState, 2);
                        }
                    }
                }

            }
        }
    }


    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) { //allow downwards growth in random ticks
        if (!state.get(CAN_GROW)) {
            return;
        }
        if (random.nextInt(5) == 0) { //slightly less common than vanilla since it's always growing down
            if (pos.getY() > world.getBottomY()) {//if we're above the bottom of the world, grow downwards
                BlockPos belowPos = pos.down();
                BlockState belowState = world.getBlockState(belowPos);
                if (belowState.isAir() || belowState.isOf(this)) {
                    BlockState nonAirBelowState = belowState.isAir() ? this.getDefaultState() : belowState;
                    BlockState grownState = this.getGrownState(state, nonAirBelowState, random);
                    if (belowState != grownState && this.hasHorizontalSide(grownState)) {
                        world.setBlockState(belowPos, grownState, 2);
                    }
                }
            }
        }
    }
}
