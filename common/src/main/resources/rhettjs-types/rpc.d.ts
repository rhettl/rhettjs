// RhettJS RPC API Type Definitions
// Version: 0.4.0
// Last updated: 2026-01-15
// Phase 2: Request/response communication (TCP-style)
// Status: Not yet implemented (definitions only)

/**
 * RPC method handler function (server-side)
 * @param caller - Caller information
 * @param params - Method parameters
 * @returns Response data or Promise
 * @throws Error to reject the RPC call
 */
export type RPCHandler<TParams = any, TResult = any> = (
    caller: RPCCaller,
    params: TParams
) => TResult | Promise<TResult>;

/**
 * Caller information for RPC handlers
 */
export interface RPCCaller {
    /** Caller's UUID */
    uuid: string;
    /** Caller's display name */
    name: string;
    /** Caller's position */
    position: { x: number; y: number; z: number };
    /** Additional caller metadata */
    [key: string]: any;
}

/**
 * RPC error thrown when method is not found
 */
export class RPCNotFoundError extends Error {
    constructor(method: string);
}

/**
 * RPC error thrown when call times out
 */
export class RPCTimeoutError extends Error {
    constructor(method: string, timeout: number);
}

/**
 * RPC call options
 */
export interface RPCOptions {
    /** Timeout in milliseconds (default: 30000) */
    timeout?: number;
}

declare module 'rhettjs/rpc' {
    /**
     * Server RPC API (Request/Response pattern)
     *
     * Provides Promise-based method calls from client to server.
     * Built on top of Network API for reliability.
     *
     * ⚠️ Phase 2: Not yet implemented
     *
     * @example
     * // SERVER: Register RPC method
     * import Server from 'rhettjs/rpc';
     *
     * Server.register('shop:purchase', (caller, params) => {
     *     const { item, quantity } = params;
     *
     *     if (caller.balance < price) {
     *         throw new Error('Insufficient funds');
     *     }
     *
     *     return {
     *         success: true,
     *         balance: caller.balance - price
     *     };
     * });
     *
     * @example
     * // CLIENT: Call server method
     * import Server from 'rhettjs/rpc';
     *
     * try {
     *     const result = await Server.call('shop:purchase', {
     *         item: 'diamond_sword',
     *         quantity: 1
     *     });
     *
     *     console.log('Purchase successful!', result);
     * } catch (error) {
     *     console.error('Purchase failed:', error.message);
     * }
     */
    export const Server: {
        /**
         * Register an RPC method handler (SERVER ONLY)
         *
         * Only one handler can be registered per method.
         * Registering again will replace the previous handler.
         *
         * @param method - Method name (e.g., 'shop:purchase')
         * @param handler - Handler function
         *
         * @example
         * Server.register('shop:buy', (caller, params) => {
         *     return { success: true };
         * });
         *
         * @example
         * // Async handler
         * Server.register('database:query', async (caller, params) => {
         *     const data = await fetchFromDatabase(params.query);
         *     return { data };
         * });
         *
         * @example
         * // Error handling
         * Server.register('auth:login', (caller, params) => {
         *     if (!validateCredentials(params)) {
         *         throw new Error('Invalid credentials');
         *     }
         *     return { token: generateToken(caller.uuid) };
         * });
         */
        register<TParams = any, TResult = any>(
            method: string,
            handler: RPCHandler<TParams, TResult>
        ): void;

        /**
         * Unregister an RPC method handler (SERVER ONLY)
         *
         * @param method - Method name
         *
         * @example
         * Server.unregister('shop:buy');
         */
        unregister(method: string): void;

        /**
         * Call a server method (CLIENT ONLY)
         *
         * Sends request to server and waits for response.
         * Returns a Promise that resolves with the result or rejects on error.
         *
         * @param method - Method name
         * @param params - Method parameters
         * @param options - Call options (timeout, etc.)
         * @returns Promise resolving to method result
         *
         * @throws RPCNotFoundError if method doesn't exist
         * @throws RPCTimeoutError if call times out
         * @throws Error if handler throws
         *
         * @example
         * const result = await Server.call('shop:purchase', {
         *     item: 'diamond_sword',
         *     quantity: 1
         * });
         *
         * @example
         * // With timeout
         * const result = await Server.call('slow:operation', params, {
         *     timeout: 60000 // 60 seconds
         * });
         */
        call<TParams = any, TResult = any>(
            method: string,
            params: TParams,
            options?: RPCOptions
        ): Promise<TResult>;

        /**
         * Get list of registered methods (SERVER ONLY)
         *
         * @returns Array of method names
         *
         * @example
         * const methods = Server.getMethods();
         * console.log('Available methods:', methods);
         */
        getMethods(): string[];

        /**
         * Check if method is registered (SERVER ONLY)
         *
         * @param method - Method name
         * @returns True if method is registered
         *
         * @example
         * if (Server.hasMethod('shop:purchase')) {
         *     console.log('Purchase method available');
         * }
         */
        hasMethod(method: string): boolean;
    };

    /**
     * Client RPC API (Bidirectional)
     *
     * Allows server to call methods on specific clients.
     *
     * ⚠️ Phase 2: Not yet implemented
     *
     * @example
     * // CLIENT: Register method
     * import Client from 'rhettjs/rpc';
     *
     * Client.register('ui:show_dialog', (params) => {
     *     const screen = UI.createDialogScreen(params);
     *     screen.show();
     *
     *     // Return user's choice
     *     return { buttonClicked: 'OK' };
     * });
     *
     * @example
     * // SERVER: Call client method
     * import Client from 'rhettjs/rpc';
     *
     * const result = await Client.call(playerUuid, 'ui:show_dialog', {
     *     title: 'Quest Complete!',
     *     message: 'You defeated the dragon'
     * });
     *
     * console.log('User clicked:', result.buttonClicked);
     */
    export const Client: {
        /**
         * Register client RPC method (CLIENT ONLY)
         *
         * @param method - Method name
         * @param handler - Handler function
         *
         * @example
         * Client.register('ui:prompt', (params) => {
         *     return { response: prompt(params.message) };
         * });
         */
        register<TParams = any, TResult = any>(
            method: string,
            handler: (params: TParams) => TResult | Promise<TResult>
        ): void;

        /**
         * Unregister client RPC method (CLIENT ONLY)
         *
         * @param method - Method name
         */
        unregister(method: string): void;

        /**
         * Call client method (SERVER ONLY)
         *
         * @param playerUuid - Target player UUID
         * @param method - Method name
         * @param params - Method parameters
         * @param options - Call options
         * @returns Promise resolving to method result
         *
         * @example
         * const choice = await Client.call(uuid, 'ui:confirm', {
         *     message: 'Accept trade?'
         * });
         */
        call<TParams = any, TResult = any>(
            playerUuid: string,
            method: string,
            params: TParams,
            options?: RPCOptions
        ): Promise<TResult>;
    };

    export { Server, Client };
    export default Server;
}

// Legacy bare module support
declare module 'Server' {
    export { Server as default } from 'rhettjs/rpc';
}
