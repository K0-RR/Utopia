@file:JvmName("ConfigDataKt")

package com.redgrapefruit.utopia.common.core

import com.redgrapefruit.utopia.common.MOD_ID
import com.redgrapefruit.utopia.common.UNUSED_PROPERTY
import com.redgrapefruit.utopia.common.UNUSED_PROPERTY_FLOAT
import kotlinx.serialization.json.*
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import java.io.InputStream
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// <----  Managing data-driven FoodConfigs  ---->

/**
 * (Re)loads [FoodConfig]s
 */
object FoodConfigReloader : SimpleSynchronousResourceReloadListener {
    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(FoodConfigReloader)
    }

    override fun reload(manager: ResourceManager) {
        // Clear FoodConfigStorage to reload everything
        FoodConfigStorage.clear()

        manager.findResources("config") { path -> path.endsWith(".config.json") }.forEach { id ->
            process(manager.getResource(id).inputStream, id)
        }
    }

    private fun process(input: InputStream, id: Identifier) {
        // Obtain the name of the food (utopia:config/almond.config.json => almond)
        val name = id.toString().remove("$MOD_ID:config/").remove(".config.json")

        // Read JsonObject
        val jsonObject: JsonObject
        input.use { stream ->
            jsonObject = Json.decodeFromString(JsonObject.serializer(), stream.readBytes().decodeToString())
        }

        // Parse everything

        // FoodCategory. This also determines which properties are required afterwards
        val category = FoodCategory.fromString(
            assertConfigProperty(jsonObject["category"], "category", name).jsonPrimitive.content)
        // Rot values
        var rotSpeed = UNUSED_PROPERTY
        var rotState = UNUSED_PROPERTY
        if (category.canRot) {
            rotSpeed = assertConfigProperty(jsonObject["rotSpeed"], "rotSpeed", name).jsonPrimitive.int
            rotState = assertConfigProperty(jsonObject["rotState"], "rotState", name).jsonPrimitive.int
        }
        // Overdue values
        var overdueSpeed = UNUSED_PROPERTY
        var overdueState = UNUSED_PROPERTY
        if (category.canOverdue) {
            overdueSpeed = assertConfigProperty(jsonObject["overdueSpeed"], "overdueSpeed", name).jsonPrimitive.int
            overdueState = assertConfigProperty(jsonObject["overdueState"], "overdueState", name).jsonPrimitive.int
        }
        // Fridge
        var fridgeEfficiency = UNUSED_PROPERTY
        if (category.canBePutInFridge) {
            fridgeEfficiency = assertConfigProperty(jsonObject["fridgeEfficiency"], "fridgeEfficiency", name).jsonPrimitive.int
        }
        // Salt
        val saltEfficiency =
            if (jsonObject.contains("saltEfficiency")) jsonObject["saltEfficiency"]!!.jsonPrimitive.int else UNUSED_PROPERTY
        // Hunger & Saturation
        val hunger = assertConfigProperty(jsonObject["hunger"], "hunger", name).jsonPrimitive.int
        val saturationModifier = assertConfigProperty(jsonObject["saturationModifier"], "saturationModifier", name).jsonPrimitive.float

        // Construct config using the DSL
        val config = config {
            this.category = category
            if (rotSpeed != UNUSED_PROPERTY) this.rotSpeed = rotSpeed
            if (rotState != UNUSED_PROPERTY) this.rotState = rotState
            if (overdueSpeed != UNUSED_PROPERTY) this.overdueSpeed = overdueSpeed
            if (overdueState != UNUSED_PROPERTY) this.overdueState = overdueState
            if (fridgeEfficiency != UNUSED_PROPERTY) this.fridgeEfficiency = fridgeEfficiency
            if (saltEfficiency != UNUSED_PROPERTY) this.saltEfficiency = saltEfficiency
            if (hunger != UNUSED_PROPERTY) this.hunger = hunger
            if (saturationModifier != UNUSED_PROPERTY_FLOAT) this.saturationModifier = saturationModifier
        }

        // Put the config in the storage
        FoodConfigStorage.put(name, config)
        // Invoke event
        ComponentInitializeCallback.Event.invoker().init(name, config)
    }

    private fun <T> assertConfigProperty(input: T?, name: String, foodName: String): T {
        return input ?: throw RuntimeException("Missing $name in config for ${foodName.replace("_", " ")}; a crash may occur")
    }

    override fun getFabricId(): Identifier = Identifier(MOD_ID, "listener")
}

/**
 * The caching storage for data-driven FoodConfigs
 */
private object FoodConfigStorage {
    private val values: MutableMap<String, FoodConfig> = mutableMapOf()

    fun get(name: String): FoodConfig {
        return values.getOrDefault(name, FoodConfig.Default)
    }

    fun put(name: String, config: FoodConfig) {
        values[name] = config
    }

    fun clear(): Unit = values.clear()
}

/**
 * A delegate that recalls [supplier] each time used.
 *
 * That allows to query the value each time it's needed since the value can update
 * (like with the cached config system).
 *
 * Instantiate via [reloaderDelegate].
 */
class ConfigReloaderDelegate(private val supplier: () -> FoodConfig) : ReadOnlyProperty<Any, FoodConfig> {
    override fun getValue(thisRef: Any, property: KProperty<*>): FoodConfig = supplier()
}

fun reloaderDelegate(supplier: () -> FoodConfig): ConfigReloaderDelegate {
    return ConfigReloaderDelegate(supplier)
}

/**
 * Gets a config from cache
 */
fun storedConfig(name: String) = FoodConfigStorage.get(name)

/**
 * Removes a [segment] from the string
 */
fun String.remove(segment: String): String = replace(segment, "")

/**
 * Allows you to prepare a component for food once the config is loaded in
 */
interface ComponentInitializeCallback {
    fun init(name: String, config: FoodConfig)

    companion object {
        val Event: Event<ComponentInitializeCallback> = EventFactory.createArrayBacked(ComponentInitializeCallback::class.java)
        { listeners: Array<ComponentInitializeCallback> ->
            Impl { name, config ->
                listeners.forEach { listener -> listener.init(name, config) }
            }
        }

        /**
         * Create a lambda listener for the event
         */
        fun listener(action: (String, FoodConfig) -> Unit) = Impl(action)
    }

    /**
     * Wrapper implementation for lambda listeners. Use [listener] to create.
     */
    class Impl(private val action: (String, FoodConfig) -> Unit) : ComponentInitializeCallback {
        override fun init(name: String, config: FoodConfig) = action(name, config)
    }
}
