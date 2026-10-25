package com.astralis.registry;

import com.astralis.AstralisMod;
import com.astralis.block.LaunchPadBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AstralisMod.MODID);

    // --- Terre ---
    public static final RegistryObject<Block> TITANIUM_ORE = registerBlock("titanium_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of(Material.STONE)
                    .strength(4.5F, 3.0F).requiresCorrectToolForDrops(), UniformInt.of(2, 5)));

    public static final RegistryObject<Block> DEEPSLATE_TITANIUM_ORE = registerBlock("deepslate_titanium_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of(Material.STONE)
                    .strength(6.0F, 3.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE), UniformInt.of(2, 5)));

    public static final RegistryObject<Block> TITANIUM_BLOCK = registerBlock("titanium_block",
            () -> new Block(BlockBehaviour.Properties.of(Material.METAL)
                    .strength(5.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    // --- Lune ---
    public static final RegistryObject<Block> MOON_ROCK = registerBlock("moon_rock",
            () -> new Block(BlockBehaviour.Properties.of(Material.STONE)
                    .strength(2.0F, 6.0F).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> MOON_REGOLITH = registerBlock("moon_regolith",
            () -> new Block(BlockBehaviour.Properties.of(Material.SAND)
                    .strength(0.6F).sound(SoundType.SAND)));

    public static final RegistryObject<Block> LUNAR_ILMENITE_ORE = registerBlock("lunar_ilmenite_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of(Material.STONE)
                    .strength(4.0F, 3.0F).requiresCorrectToolForDrops(), UniformInt.of(2, 5)));

    // --- Transport ---
    public static final RegistryObject<Block> LAUNCH_PAD = registerBlock("launch_pad",
            () -> new LaunchPadBlock(BlockBehaviour.Properties.of(Material.METAL)
                    .strength(4.0F, 20.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> reg = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(reg.get(), new Item.Properties().tab(AstralisMod.TAB)));
        return reg;
    }
}
