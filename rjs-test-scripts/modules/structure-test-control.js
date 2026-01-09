import WorldgenStructure from "rhettjs/worldgen-structure";
import World from "rhettjs/world";
import {LargeStructureNbt} from "rhettjs/structure";

// export const store = {
//   position: Store.namespace('stc:position'),
//   getPosition (name, posNum) {
//     return this.positions.get(`${name}:pos${posNum}`);
//   },
//   setPosition (name, posNum) {
//     return this.positions.set(`${name}:pos${posNum}`);
//   },
//   clearPosition (name, posNum) {
//     return this.positions.delete(`${name}:pos${posNum}`);
//   },
// }

export const platform = {
  center: {x: 0, y: 63, z: 0, dimension: 'rhettjs:structure-test'},
  controlPlatformBounds: {
    // min: {x: 0, y: 63, z: 0, dimension: 'rhettjs:structure-test'},
    // max: {x: 0, y: 63, z: 0, dimension: 'rhettjs:structure-test'}
  },
  platformRadius: 150, // 301x301 space around center

  /**
   *
   * @param {Position} min -- Lowest NW Corner
   * @param {Position} max -- Highest SE Corner
   * @returns {platform}
   */
  setControlPlatformBounds (min, max) {
    this.controlPlatformBounds.min = {
      ...this.controlPlatformBounds.min,
      ...min
    };
    this.controlPlatformBounds.max = {
      ...this.controlPlatformBounds.max,
      ...max
    };
    return this;
  },

  /**
   * @param {Position} pos
   * @returns {platform}
   */
  setCenter (pos) {
    this.center = {
      dimension: 'rhettjs:structure-test',
      ...this.center,
      ...pos
    };
    return this;
  },

  async placeVillage (name, options = {}) {
    this.lastPlacement = null;
    if (!await WorldgenStructure.exists(name)) {
      throw new Error(`Worldgen structure does not exist: ${name}`);
    }

    this.lastPlacement = await WorldgenStructure.place(name, {
      x: this.center.x,
      z: this.center.z,
      dimension: this.center.dimension,
      surface: 'scan',

      ...options,
    });

    return this;
  },

  async placePlatform (name, options = {}) {
    this.lastPlatformPlacement = null;
    name = name.includes(':') ? name : `minecraft:${name}`;
    if (!await largeStructureExists(name)) {
      throw new Error(`Large structure does not exist: ${name}`);
    }

    console.log(`placing ${name}`, options);
    this.lastPlatformPlacement = await LargeStructureNbt.place(
      this.center,
      name,
      {
        centered: true,
        vAlign: "center",
      }
    )
    return this;
  },



  async clear ({radius = this.platformRadius, exclusions = []} = {}) {
    let pos1 = {
      x: this.center.x - radius,
      y: 0,
      z: this.center.z - radius,
      dimension: this.center.dimension
    };
    let pos2 = {
      x: this.center.x + radius,
      y: 0,
      z: this.center.z + radius,
      dimension: this.center.dimension
    };

    const bounds = await World.getFilledBounds(
      pos1, pos2
    );

    if (!bounds) {
      console.log('region already clear')
      return this;
    }

    await World.fill(
      { ...pos1, y: bounds.minY },
      { ...pos2, y: bounds.maxY },
      "minecraft:air",
      {
        exclude: [
          ...(this.controlPlatformBounds?.min && this.controlPlatformBounds?.max ? [this.controlPlatformBounds] : []),
          ...exclusions,
        ]
      }
    );
    await World.removeEntities(
      { ...pos1, y: bounds.minY },
      { ...pos2, y: bounds.maxY+8 },
      { excludePlayers: true }
    );

    return this;
  }

};

export const read = {
  async sign (pos) {
    const sign = await World.getBlockEntity(pos);
    if (!sign) {
      throw new Error(`Unable to read block entity at position ${pos.x}, ${pos.y}, ${pos.z}`);
    }

    if (!sign.front_text) {
      return {
        hasSign: false,
        front: {
          rows: [],
          combine: () => '',
        },
        back: {
          rows: [],
          combine: () => '',
        },
      }
    }

    const front = sign.front_text?.messages.map(i => JSON.parse(i)) ?? [];
    const back = sign.back_text?.messages.map(i => JSON.parse(i)) ?? [];
    return {
      hasSign: true,
      front: {
        rows: front,
        combine: () => front.join('').trim().replace(/ +/g, ''),
      },
      back: {
        rows: back,
        combine: () => back.join('').trim().replace(/ +/g, ''),
      },
    }
  },

  async lectern (pos) {
    const lectern = await World.getBlockEntity(pos);
    if (!lectern) {
      throw new Error(`Unable to read block entity at position ${pos.x}, ${pos.y}, ${pos.z}`);
    }

    // No book in lectern
    if (!lectern.Book) {
      return {
        hasBook: false,
        pages: [],
        currentPageNum: 0,
        pageCount: 0,
        name: null,
        author: null,
        currentPage: null
      };
    }
    /*
      {
        Book={
          components={
            minecraft:writable_book_content={
              pages=[
                {raw=Content}
              ]
            }
          },
          count=1,
          id=minecraft:writable_book
        },
        Page=0
      }
    */


    // Parse book data - new 1.21 component format
    const components = lectern.Book.components || {};
    const bookContent = components["minecraft:writable_book_content"] || {};
    const rawPages = bookContent.pages || [];
    const currentPageNum = lectern.Page || 0;

    // Parse all pages - each page has a 'raw' field with the text content
    const pages = rawPages.map(pageData => {
      const text = pageData.raw || '';
      const rows = text.split('\n');
      return {
        rows,
        text,
        combine: () => rows.join('').trim().replace(/ +/g, '')
      };
    });

    const currentPage = pages[currentPageNum] || null;

    return {
      hasBook: true,
      pages,
      currentPageNum,
      pageCount: pages.length,
      name: null, // Title not in writable_book_content, would be in written_book_content
      author: null,
      currentPage,
      // Helper to get specific page
      getPage: (num) => pages[num] || null
    };
  }
};


async function largeStructureExists (name) {
  name = name.includes(':') ? name : `minecraft:${name}`;
  let structures = await LargeStructureNbt.list();
  return !!structures.find(s => s === name);
}


