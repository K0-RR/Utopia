package com.redgrapefruit.utopia.item

import com.redgrapefruit.utopia.GROUP
import com.redgrapefruit.utopia.core.RFoodCategory
import com.redgrapefruit.utopia.core.RFoodConfig
import com.redgrapefruit.utopia.core.RFoodProfile
import com.redgrapefruit.utopia.core.RFoodState
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.item.FoodComponent
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup

/**
 * Represents a fresh food item as well as a base for other variants
 */
@Suppress("JoinDeclarationAndAssignment")
open class RFoodItem : Item {
    private val config: RFoodConfig
    val profile: RFoodProfile
    protected var state: RFoodState = RFoodState.FRESH
    protected var overrideEffects: Boolean = false

    /**
     * Protected constructor for creating customized [FoodComponent] instances
     * @param config [RFoodConfig] of this food item
     * @param group The [ItemGroup] that the item belongs to
     * @param componentAction A lambda that returns the generated [FoodComponent]
     */
    protected constructor(
        config: RFoodConfig,
        group: ItemGroup,
        componentAction: () -> FoodComponent
    ) : super(Settings().group(group).food(componentAction.invoke())) {
        this.config = config
        this.profile = RFoodProfile()
    }

    /**
     * Public constructor for creating standard instances of food items (fresh)
     * @param config [RFoodConfig] of this food item
     */
    constructor(config: RFoodConfig) : this(config, GROUP, {
        val builder = FoodComponent.Builder()

        // Hunger
        builder.hunger(config.category.baseHunger + config.hunger)
        // Meat
        if (config.category == RFoodCategory.MEAT) builder.meat()
        // Snack
        if (config.category.baseHunger + config.hunger < 2) builder.snack()
        // Saturation modifier
        builder.saturationModifier(config.category.baseSaturationModifier + config.saturationModifier)
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

            builder.statusEffect(
                StatusEffectInstance(
                    effectConfig.statusEffect,
                    duration,
                    amplifier
                ), if (effectConfig.isAlwaysApplied) 1f else effectConfig.chance
            )
        }

        builder.build()
    })
}