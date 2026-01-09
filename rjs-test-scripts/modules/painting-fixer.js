/**
 * Painting Fixer Module
 *
 * Pure business logic for analyzing and fixing painting entity NBT data.
 * Works with plain JavaScript objects - no Minecraft types.
 *
 * @module painting-fixer
 */

// ============================================================================
// Configuration
// ============================================================================

const CONFIG = {
  CORRECT_Y_OFFSET: 0.5,      // Paintings should be at block Y + 0.5
  TOLERANCE: 0.01,            // Floating point comparison tolerance
  CENTERING_THRESHOLD: 0.4,   // If offset is not within 0.4 of 0.5, needs fixing
};

const FACING_NAMES = ['south', 'west', 'north', 'east'];

// ============================================================================
// Models
// ============================================================================

/**
 * @typedef {Object} PaintingEntity
 * @property {[number, number, number]} blockPos - Block position
 * @property {[number, number, number]} pos - Entity position
 * @property {Object} nbt - NBT data
 * @property {string} nbt.id - Entity type (should be "minecraft:painting")
 * @property {string} [nbt.variant] - Painting variant
 * @property {number} [nbt.facing] - Facing direction (0=south, 1=west, 2=north, 3=east)
 */

/**
 * @typedef {Object} PaintingIssue
 * @property {number} index - Painting index in structure
 * @property {PaintingEntity} entity - Reference to original entity
 * @property {string} variant - Painting variant
 * @property {string} facing - Facing direction name
 * @property {[number, number, number]} blockPos - Block position
 * @property {[number, number, number]} currentPos - Current entity position
 * @property {[number, number, number]} correctPos - Corrected position
 * @property {boolean} yOffsetIncorrect - Y offset needs fixing
 * @property {boolean} centeringIncorrect - Centering needs fixing
 * @property {string} centeringAxis - Which axis needs centering (X or Z)
 */

/**
 * @typedef {Object} AnalysisResult
 * @property {number} totalEntities - Total entities in structure
 * @property {number} totalPaintings - Total painting entities
 * @property {PaintingIssue[]} issues - Paintings with issues
 * @property {number} correctCount - Count of correct paintings
 */

// ============================================================================
// Analysis Functions
// ============================================================================

/**
 * Analyze painting entities in structure data
 * Pure function - does not modify input
 *
 * @param {Object} structureData - Structure NBT data
 * @param {Array} structureData.entities - Array of entities
 * @returns {AnalysisResult} Analysis results
 */
export function analyzePaintings(structureData) {
  const result = {
    totalEntities: 0,
    totalPaintings: 0,
    issues: [],
    correctCount: 0,
  };

  if (!structureData?.entities) {
    return result;
  }

  result.totalEntities = structureData.entities.length;

  // Filter to paintings only
  const paintings = structureData.entities.filter(
    entity => entity?.nbt?.id === 'minecraft:painting'
  );

  result.totalPaintings = paintings.length;

  // Analyze each painting
  paintings.forEach((entity, index) => {
    const issue = analyzePainting(entity, index);

    if (issue) {
      result.issues.push(issue);
    } else {
      result.correctCount++;
    }
  });

  return result;
}

/**
 * Analyze a single painting entity
 * Pure function - does not modify input
 *
 * @param {PaintingEntity} entity - Painting entity
 * @param {number} index - Entity index
 * @returns {PaintingIssue | null} Issue object if problems found, null if correct
 */
function analyzePainting(entity, index) {
  const { blockPos, pos, nbt } = entity;

  if (!blockPos || !pos) {
    return null; // Can't analyze without position data
  }

  const [blockX, blockY, blockZ] = blockPos;
  const [x, y, z] = pos;

  // Calculate offsets
  const xOffset = x - blockX;
  const yOffset = y - blockY;
  const zOffset = z - blockZ;

  // Extract painting metadata
  const variant = nbt?.variant || 'unknown';
  const facing = nbt?.facing ?? -1;
  const facingName = FACING_NAMES[facing] || 'unknown';

  // Check Y offset
  const yOffsetIncorrect = Math.abs(yOffset - CONFIG.CORRECT_Y_OFFSET) > CONFIG.TOLERANCE;

  // Check centering based on facing
  // South/North (0/2): X should be 0.5
  // West/East (1/3): Z should be 0.5
  let centeringIncorrect = false;
  let centeringAxis = '';

  if (facing === 0 || facing === 2) {
    centeringAxis = 'X';
    centeringIncorrect = Math.abs(xOffset - 0.5) > CONFIG.CENTERING_THRESHOLD;
  } else if (facing === 1 || facing === 3) {
    centeringAxis = 'Z';
    centeringIncorrect = Math.abs(zOffset - 0.5) > CONFIG.CENTERING_THRESHOLD;
  }

  // If no issues, return null
  if (!yOffsetIncorrect && !centeringIncorrect) {
    return null;
  }

  // Calculate corrected position
  let correctedX = x;
  let correctedY = yOffsetIncorrect ? (blockY + CONFIG.CORRECT_Y_OFFSET) : y;
  let correctedZ = z;

  if (centeringIncorrect) {
    if (facing === 0 || facing === 2) {
      correctedX = blockX + 0.5;
    } else if (facing === 1 || facing === 3) {
      correctedZ = blockZ + 0.5;
    }
  }

  return {
    index,
    entity,
    variant,
    facing: facingName,
    blockPos: [blockX, blockY, blockZ],
    currentPos: [x, y, z],
    correctPos: [correctedX, correctedY, correctedZ],
    yOffsetIncorrect,
    centeringIncorrect,
    centeringAxis,
  };
}

// ============================================================================
// Fix Functions
// ============================================================================

/**
 * Apply fixes to painting entities
 * MUTATES the structure data in place
 *
 * @param {Object} structureData - Structure NBT data (will be modified)
 * @param {PaintingIssue[]} issues - Issues to fix
 * @returns {number} Number of paintings fixed
 */
export function applyFixes(structureData, issues) {
  let fixedCount = 0;

  issues.forEach(issue => {
    if (issue.entity?.pos) {
      // Mutate entity position in place
      issue.entity.pos[0] = issue.correctPos[0];
      issue.entity.pos[1] = issue.correctPos[1];
      issue.entity.pos[2] = issue.correctPos[2];
      fixedCount++;
    }
  });

  return fixedCount;
}

/**
 * Analyze and fix paintings in one operation
 * MUTATES the structure data
 *
 * @param {Object} structureData - Structure NBT data (will be modified)
 * @returns {Object} Result with analysis and fix count
 */
export function analyzeAndFix(structureData) {
  const analysis = analyzePaintings(structureData);

  if (analysis.issues.length === 0) {
    return {
      ...analysis,
      fixed: 0,
    };
  }

  const fixed = applyFixes(structureData, analysis.issues);

  return {
    ...analysis,
    fixed,
  };
}

// ============================================================================
// Formatting Helpers
// ============================================================================

/**
 * Format issue for display
 * Pure function for presentation layer
 *
 * @param {PaintingIssue} issue - Issue to format
 * @returns {Object} Formatted issue details
 */
export function formatIssue(issue) {
  const issueTypes = [];
  if (issue.yOffsetIncorrect) issueTypes.push('Y-offset');
  if (issue.centeringIncorrect) issueTypes.push(`${issue.centeringAxis}-centering`);

  return {
    summary: `#${issue.index + 1} (${issue.variant}) facing ${issue.facing}`,
    issueTypes: issueTypes.join(', '),
    blockPos: formatPosition(issue.blockPos),
    currentPos: formatPosition(issue.currentPos),
    correctPos: formatPosition(issue.correctPos),
  };
}

/**
 * Format position array for display
 *
 * @param {[number, number, number]} pos - Position
 * @returns {string} Formatted position
 */
function formatPosition(pos) {
  return `[${pos[0].toFixed(2)}, ${pos[1].toFixed(2)}, ${pos[2].toFixed(2)}]`;
}