package com.astralis.item;

import com.astralis.entity.RocketEntity;
import com.astralis.registry.ModBlocks;
import com.astralis.registry.ModEntities;
import com.astralis.space.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Fusée à poser sur une plateforme de lancement (Terre ou Lune). Conserve le carburant restant. */
public class RocketItem extends Item {
    public RocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!level.getBlockState(pos).is(ModBlocks.LAUNCH_PAD.get())) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("msg.astralis.rocket_place_pad"), true);
            }
            return InteractionResult.FAIL;
        }

        int body;
        if (level.dimension().equals(Level.OVERWORLD)) {
            body = RocketEntity.EARTH;
        } else if (level.dimension().equals(ModDimensions.MOON)) {
            body = RocketEntity.MOON;
        } else {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("msg.astralis.bad_dimension"), true);
            }
            return InteractionResult.FAIL;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        AABB zone = new AABB(pos).inflate(2.0, 0.0, 2.0).expandTowards(0.0, 10.0, 0.0);
        if (!level.getEntitiesOfClass(RocketEntity.class, zone).isEmpty()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("msg.astralis.rocket_exists"), true);
            }
            return InteractionResult.FAIL;
        }

        RocketEntity rocket = ModEntities.ROCKET.get().create(level);
        if (rocket == null) {
            return InteractionResult.FAIL;
        }
        rocket.moveTo(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5,
                player != null ? player.getYRot() : 0.0F, 0.0F);
        rocket.setBody(body);
        rocket.setPad(pos.getX(), pos.getZ());

        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Fuel")) {
            rocket.setFuel(tag.getFloat("Fuel"));
        }
        level.addFreshEntity(rocket);

        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        float fuel = tag == null ? 0.0F : tag.getFloat("Fuel");
        tooltip.add(Component.translatable("tooltip.astralis.rocket_fuel", Math.round(fuel),
                Math.round(RocketEntity.MAX_FUEL)).withStyle(ChatFormatting.GRAY));
    }
}
