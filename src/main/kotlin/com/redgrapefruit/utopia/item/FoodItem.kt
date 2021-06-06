package com.redgrapefruit.utopia.item

import com.redgrapefruit.utopia.core.FoodCategory
import com.redgrapefruit.utopia.core.FoodConfig
import com.redgrapefruit.utopia.core.FoodState
import com.redgrapefruit.utopia.core.ProfileComponent
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.item.FoodComponent
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup

/**
 * Represents a fresh food item as well as a base for other variants
 */
@Suppress("JoinDeclarationAndAssignment")
open class FoodItem : Item {
    private val config: FoodConfig
    private val profile: ProfileComponent
    private var state: FoodState = FoodState.FRESH
    private var overrideEffects: Boolean = false

    /**
     * Protected constructor for creating customized [FoodComponent] instances
     * @param config [FoodConfig] of this food item
     * @param group The [ItemGroup] that the item belongs to
     * @param componentAction A lambda that returns the generated [FoodComponent]
     */
    protected constructor(config: FoodConfig, group: ItemGroup, componentAction: () -> FoodComponent) : super(Item.Settings().group(group).food(componentAction.invoke())) {
        this.config = config
        this.profile = ProfileComponent()
    }

    /**
     * Public constructor for creating standard instances of food items (fresh)
     * @param config [FoodConfig] of this food item
     */
    // TODO: Replace with actual ItemGroup later
    public constructor(config: FoodConfig) : this(config, ItemGroup.MISC, {
        val builder = FoodComponent.Builder()

        // Meat
        if (config.category == FoodCategory.MEAT) builder.meat()
        // Effects
        config.effects.forEach { effectConfig ->
            // If durationRange isn't defined (null), then check if the effect is permanent, if yes, the duration is 999999, else it's effectConfig.duration
            // Else pick a random duration out of the durationRange using IntRange.pick
            val duration = if (effectConfig.durationRange == null)
                    (if (effectConfig.isPermanent) 999999 else effectConfig.duration)
                    else effectConfig.durationRange.pick()

            // If amplifierRange isn't defined (null), then use effectConfig.amplifier
            // Else pick a random amplifier out of the amplifierRange using IntRange.pick
            val amplifier = if (effectConfig.amplifierRange == null)
                effectConfig.amplifier
                else effectConfig.amplifierRange.pick()

            builder.statusEffect(StatusEffectInstance(
                effectConfig.statusEffect,
                duration,
                amplifier
            ), if (effectConfig.isAlwaysApplied) 1f else effectConfig.chance)
        }

        builder.build()
    })
}