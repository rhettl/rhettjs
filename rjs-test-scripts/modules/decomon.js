import World from 'rhettjs/world';
import Store from 'rhettjs/store';
import NBT from 'rhettjs/nbt';
import Server from 'rhettjs/server';

// ============================================
// DECOMON - Decorative Cobblemon Manager
// ============================================
// JavaScript implementation of the Decomon datapack
// for managing decorative Cobblemon entities

const STORE_PREFIX = "decomon:";
const ENTITY_TAG = "decomon";
const SELECTION_DISTANCE = 10;

/**
 * Manager class for decorative Cobblemon entities
 */
export class DecomonManager {
  constructor() {
    this._initializeStore();
  }

  /**
   * Initialize persistent storage
   * @private
   */
  _initializeStore() {
    // Initialize next ID counter if it doesn't exist
    if (!Store.has(`${STORE_PREFIX}nextId`)) {
      Store.set(`${STORE_PREFIX}nextId`, 1);
    }

    // Initialize selections object if it doesn't exist
    if (!Store.has(`${STORE_PREFIX}selections`)) {
      Store.set(`${STORE_PREFIX}selections`, {});
    }

    // Initialize entities tracking if it doesn't exist
    if (!Store.has(`${STORE_PREFIX}entities`)) {
      Store.set(`${STORE_PREFIX}entities`, {});
    }
  }

  /**
   * Get next unique ID for a decomon
   * @private
   */
  _getNextId() {
    const nextId = Store.get(`${STORE_PREFIX}nextId`) || 1;
    Store.set(`${STORE_PREFIX}nextId`, nextId + 1);
    return nextId;
  }

  /**
   * Get all selections
   * @private
   */
  _getSelections() {
    return Store.get(`${STORE_PREFIX}selections`) || {};
  }

  /**
   * Save selections
   * @private
   */
  _saveSelections(selections) {
    Store.set(`${STORE_PREFIX}selections`, selections);
  }

  /**
   * Get all tracked entities
   * @private
   */
  _getEntities() {
    return Store.get(`${STORE_PREFIX}entities`) || {};
  }

  /**
   * Save tracked entities
   * @private
   */
  _saveEntities(entities) {
    Store.set(`${STORE_PREFIX}entities`, entities);
  }

  /**
   * Select a decorative Cobblemon by entity UUID
   * @param {string} playerUuid - Player's UUID
   * @param {string} entityUuid - Entity's UUID
   */
  select(playerUuid, entityUuid) {
    const selections = this._getSelections();
    selections[playerUuid] = entityUuid;
    this._saveSelections(selections);
  }

  /**
   * Get the currently selected entity for a player
   * @param {string} playerUuid - Player's UUID
   * @returns {string|null} Entity UUID or null if none selected
   */
  getSelected(playerUuid) {
    const selections = this._getSelections();
    return selections[playerUuid] || null;
  }

  /**
   * Select the nearest decorative Cobblemon
   * @param {object} caller - Command caller object with uuid and position
   * @returns {boolean} True if selection successful
   */
  selectNearest(caller) {
    // Find nearest cobblemon:pokemon with decomon tag within range
    const nearbyEntities = World.getEntitiesNear(
      caller.position,
      SELECTION_DISTANCE,
      { type: "cobblemon:pokemon" }
    );

    // Filter for decomon tagged entities
    const decomons = nearbyEntities.filter(entity => {
      const nbt = NBT.getEntityData(entity.uuid);
      return nbt && nbt.Tags && nbt.Tags.includes(ENTITY_TAG);
    });

    if (decomons.length === 0) {
      return false;
    }

    // Sort by distance and select nearest
    const nearest = decomons.sort((a, b) => {
      const distA = this._distance(caller.position, a.position);
      const distB = this._distance(caller.position, b.position);
      return distA - distB;
    })[0];

    this.select(caller.uuid, nearest.uuid);
    return true;
  }

  /**
   * Calculate distance between two positions
   * @private
   */
  _distance(pos1, pos2) {
    const dx = pos1.x - pos2.x;
    const dy = pos1.y - pos2.y;
    const dz = pos1.z - pos2.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * Spawn a decorative Cobblemon
   * @param {object} position - {x, y, z} spawn position
   * @param {object} options - {species, level, shiny, scale}
   * @returns {string|null} Entity UUID or null if spawn failed
   */
  spawn(position, options = {}) {
    const {
      species,
      level = 50,
      shiny = false,
      scale = 100
    } = options;

    if (!species) {
      throw new Error("Species is required for spawning");
    }

    // Execute pokespawn command
    const spawnCmd = `/pokespawn ${species} level=${level} x=${position.x} y=${position.y} z=${position.z}`;
    Server.executeCommand(spawnCmd);

    // Wait a tick for the entity to spawn, then tag it
    // Note: In real implementation, this would need proper async handling
    // or a callback system to wait for spawn completion

    // For now, return a placeholder - actual implementation needs
    // proper spawn event handling or entity query after spawn

    // Find the newly spawned pokemon (closest non-decomon entity)
    const nearby = World.getEntitiesNear(
      position,
      2,
      { type: "cobblemon:pokemon" }
    );

    const newEntity = nearby.find(entity => {
      const nbt = NBT.getEntityData(entity.uuid);
      return nbt && (!nbt.Tags || !nbt.Tags.includes(ENTITY_TAG));
    });

    if (!newEntity) {
      return null;
    }

    // Convert to decomon
    const entityUuid = newEntity.uuid;
    this._makeDecomon(entityUuid, { shiny, scale });

    // Track the entity
    const entities = this._getEntities();
    const decomonId = this._getNextId();
    entities[decomonId] = {
      uuid: entityUuid,
      species,
      level,
      spawnedAt: Date.now()
    };
    this._saveEntities(entities);

    return entityUuid;
  }

  /**
   * Convert an entity to a decorative Cobblemon
   * @private
   */
  _makeDecomon(entityUuid, options = {}) {
    const { shiny = false, scale = 100 } = options;

    // Get current NBT
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt) {
      throw new Error(`Entity ${entityUuid} not found`);
    }

    // Add decomon tag
    if (!nbt.Tags) {
      nbt.Tags = [];
    }
    if (!nbt.Tags.includes(ENTITY_TAG)) {
      nbt.Tags.push(ENTITY_TAG);
    }

    // Apply default properties
    nbt.PersistenceRequired = true;
    nbt.Health = 5000;
    nbt.CustomNameVisible = true;

    // Set attributes
    if (!nbt.attributes) {
      nbt.attributes = [];
    }
    const healthAttr = nbt.attributes.find(a => a.id === "minecraft:generic.max_health");
    if (healthAttr) {
      healthAttr.base = 5000;
    } else {
      nbt.attributes.push({
        id: "minecraft:generic.max_health",
        base: 5000
      });
    }

    // Configure movement/behavior
    if (!nbt.Config) {
      nbt.Config = {};
    }
    nbt.Config.walk_speed = 0.35;
    nbt.Config.vertical_wander_range = 5.0;
    nbt.Config.wanders = 0.0;  // Disabled by default
    nbt.Config.horizontal_wander_range = 10.0;
    nbt.Config.look_at_entity_types = "minecraft:player";
    nbt.Config.see_distance = 15.0;
    nbt.Config.wander_chance = 0.008333334;

    // Set Pokemon properties
    if (!nbt.Pokemon) {
      nbt.Pokemon = {};
    }
    nbt.Pokemon.Shiny = shiny;

    // Set scale
    if (scale !== 100) {
      nbt.Pokemon.scaleModifier = scale / 100.0;
    }

    // Apply defaults for toggleable properties
    nbt.Invulnerable = false;  // Can toggle later

    // Write back NBT
    NBT.setEntityData(entityUuid, nbt);
  }

  /**
   * Toggle shiny status
   * @param {string} entityUuid - Entity UUID
   * @returns {boolean} New shiny state
   */
  toggleShiny(entityUuid) {
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt || !nbt.Pokemon) {
      throw new Error(`Entity ${entityUuid} is not a valid Pokemon`);
    }

    const currentShiny = nbt.Pokemon.Shiny || false;
    nbt.Pokemon.Shiny = !currentShiny;
    NBT.setEntityData(entityUuid, nbt);

    return nbt.Pokemon.Shiny;
  }

  /**
   * Toggle wandering behavior
   * @param {string} entityUuid - Entity UUID
   * @returns {boolean} New wandering state
   */
  toggleWandering(entityUuid) {
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt || !nbt.Config) {
      throw new Error(`Entity ${entityUuid} has invalid Config`);
    }

    const currentWandering = nbt.Config.wanders > 0;

    if (currentWandering) {
      // Disable wandering
      nbt.Config.wanders = 0.0;
      nbt.Config.walk_speed = 0.0;
    } else {
      // Enable wandering
      nbt.Config.wanders = 1.0;
      nbt.Config.walk_speed = 0.35;
    }

    NBT.setEntityData(entityUuid, nbt);
    return !currentWandering;
  }

  /**
   * Toggle invulnerability
   * @param {string} entityUuid - Entity UUID
   * @returns {boolean} New invulnerable state
   */
  toggleInvulnerable(entityUuid) {
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt) {
      throw new Error(`Entity ${entityUuid} not found`);
    }

    const currentInvuln = nbt.Invulnerable || false;
    nbt.Invulnerable = !currentInvuln;
    NBT.setEntityData(entityUuid, nbt);

    return nbt.Invulnerable;
  }

  /**
   * Toggle "looks at player" behavior
   * @param {string} entityUuid - Entity UUID
   * @returns {boolean} New state
   */
  toggleLooksAtPlayer(entityUuid) {
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt || !nbt.Config) {
      throw new Error(`Entity ${entityUuid} has invalid Config`);
    }

    const currentLooks = nbt.Config.look_at_entity_types === "minecraft:player";
    nbt.Config.look_at_entity_types = currentLooks ? "" : "minecraft:player";
    NBT.setEntityData(entityUuid, nbt);

    return !currentLooks;
  }

  /**
   * Toggle AI (NoAI tag)
   * @param {string} entityUuid - Entity UUID
   * @returns {boolean} New NoAI state
   */
  toggleNoAI(entityUuid) {
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt) {
      throw new Error(`Entity ${entityUuid} not found`);
    }

    const currentNoAI = nbt.NoAI || false;
    nbt.NoAI = !currentNoAI;
    NBT.setEntityData(entityUuid, nbt);

    return nbt.NoAI;
  }

  /**
   * Set Pokemon level
   * @param {string} entityUuid - Entity UUID
   * @param {number} level - New level (1-100)
   */
  setLevel(entityUuid, level) {
    if (level < 1 || level > 100) {
      throw new Error("Level must be between 1 and 100");
    }

    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt || !nbt.Pokemon) {
      throw new Error(`Entity ${entityUuid} is not a valid Pokemon`);
    }

    nbt.Pokemon.level = level;
    NBT.setEntityData(entityUuid, nbt);
  }

  /**
   * Set scale
   * @param {string} entityUuid - Entity UUID
   * @param {number} scalePercent - Scale as percentage (e.g., 100 = 1.0x, 200 = 2.0x)
   */
  setScale(entityUuid, scalePercent) {
    if (scalePercent < 5 || scalePercent > 1000) {
      throw new Error("Scale must be between 5% and 1000%");
    }

    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt || !nbt.Pokemon) {
      throw new Error(`Entity ${entityUuid} is not a valid Pokemon`);
    }

    nbt.Pokemon.scaleModifier = scalePercent / 100.0;
    NBT.setEntityData(entityUuid, nbt);
  }

  /**
   * Rename entity
   * @param {string} entityUuid - Entity UUID
   * @param {string} name - New custom name
   */
  rename(entityUuid, name) {
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt) {
      throw new Error(`Entity ${entityUuid} not found`);
    }

    nbt.CustomName = JSON.stringify({ text: name });
    nbt.CustomNameVisible = true;
    NBT.setEntityData(entityUuid, nbt);
  }

  /**
   * Set pose
   * @param {string} entityUuid - Entity UUID
   * @param {string} pose - Pose name (stand, sit, fly, hover, sleep, float, glide, swim, walk)
   */
  setPose(entityUuid, pose) {
    const validPoses = ["stand", "sit", "fly", "hover", "sleep", "float", "glide", "swim", "walk"];
    if (!validPoses.includes(pose)) {
      throw new Error(`Invalid pose: ${pose}. Must be one of: ${validPoses.join(", ")}`);
    }

    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt || !nbt.Pokemon) {
      throw new Error(`Entity ${entityUuid} is not a valid Pokemon`);
    }

    nbt.Pokemon.pose = pose;
    NBT.setEntityData(entityUuid, nbt);
  }

  /**
   * Move entity to position
   * @param {string} entityUuid - Entity UUID
   * @param {object} position - {x, y, z}
   */
  move(entityUuid, position) {
    Server.executeCommand(
      `/tp ${entityUuid} ${position.x} ${position.y} ${position.z}`
    );
  }

  /**
   * Highlight entity with glowing effect
   * @param {string} entityUuid - Entity UUID
   * @param {number} durationSeconds - Duration in seconds (default 10)
   */
  highlight(entityUuid, durationSeconds = 10) {
    const ticks = durationSeconds * 20;
    Server.executeCommand(
      `/effect give ${entityUuid} minecraft:glowing ${durationSeconds} 0 true`
    );
  }

  /**
   * Remove/kill entity
   * @param {string} entityUuid - Entity UUID
   */
  remove(entityUuid) {
    Server.executeCommand(`/kill ${entityUuid}`);

    // Remove from tracking
    const entities = this._getEntities();
    const entityEntry = Object.entries(entities).find(([_, data]) => data.uuid === entityUuid);
    if (entityEntry) {
      delete entities[entityEntry[0]];
      this._saveEntities(entities);
    }

    // Clear selections pointing to this entity
    const selections = this._getSelections();
    Object.keys(selections).forEach(playerUuid => {
      if (selections[playerUuid] === entityUuid) {
        delete selections[playerUuid];
      }
    });
    this._saveSelections(selections);
  }

  /**
   * Get info about entity
   * @param {string} entityUuid - Entity UUID
   * @returns {object} Entity info
   */
  getInfo(entityUuid) {
    const nbt = NBT.getEntityData(entityUuid);
    if (!nbt) {
      throw new Error(`Entity ${entityUuid} not found`);
    }

    const entities = this._getEntities();
    const entityEntry = Object.entries(entities).find(([_, data]) => data.uuid === entityUuid);

    return {
      uuid: entityUuid,
      species: entityEntry ? entityEntry[1].species : "Unknown",
      level: nbt.Pokemon?.level || "Unknown",
      shiny: nbt.Pokemon?.Shiny || false,
      scale: (nbt.Pokemon?.scaleModifier || 1.0) * 100,
      wandering: (nbt.Config?.wanders || 0) > 0,
      invulnerable: nbt.Invulnerable || false,
      looksAtPlayer: nbt.Config?.look_at_entity_types === "minecraft:player",
      noAI: nbt.NoAI || false,
      pose: nbt.Pokemon?.pose || "unknown",
      customName: nbt.CustomName ? JSON.parse(nbt.CustomName).text : null
    };
  }

  /**
   * List all tracked decorative Cobblemon
   * @returns {Array} Array of entity info objects
   */
  listAll() {
    const entities = this._getEntities();
    return Object.entries(entities).map(([id, data]) => {
      try {
        return {
          id: parseInt(id),
          ...this.getInfo(data.uuid)
        };
      } catch (e) {
        // Entity no longer exists
        return null;
      }
    }).filter(Boolean);
  }
}

// Export singleton instance
export const decomon = new DecomonManager();

// Export class for custom instances
export default DecomonManager;
