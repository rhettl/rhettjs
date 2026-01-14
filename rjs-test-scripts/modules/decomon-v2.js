import Store from "rhettjs/store";
import Server from "rhettjs/server";
import { effect } from "./command-helpers.js"


const run = (str) => Server.runCommand(str);

const namespace = 'decomon:';

class DecomonV2 {

  _store = null;
  player = '';

  constructor (player) {
    if (!player || typeof player !== 'string') {
      throw new Error(`Player not provided: ${player}`);
    }
    this.player = player
    this._store = Store.player(player);
  }

  select (uuid) {
    this._store.set(namespace + 'target', uuid);
  }

  /**
   *
   * return {string} uuid of target
   */
  target () {
    return this._store.get(namespace + 'target');
  }

  highlight () {
    return run(effect(
      'give',
      this.target(),
      'glow',
      300,
      0,
      true
    ))
  }



}

const accessors = {
  'name': {
    set(uuid, val) {}
  }
}


export default DecomonV2;