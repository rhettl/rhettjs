/**
 * UI module for RhettJS - Create interactive screens and widgets
 *
 * Create custom UI screens with buttons, labels, images, and panels.
 * All UI operations are client-side only and synchronous.
 *
 * @example
 * ```javascript
 * import UI from 'rhettjs/ui';
 *
 * const screen = UI.createScreen('my-menu');
 *
 * screen.addLabel({
 *   x: 100,
 *   y: 50,
 *   text: 'Welcome!',
 *   color: 0xFFFFFF
 * });
 *
 * screen.addButton({
 *   x: 100,
 *   y: 100,
 *   width: 100,
 *   height: 20,
 *   text: 'Click Me',
 *   onClick: () => console.log('Button clicked!')
 * });
 *
 * screen.show();
 * ```
 *
 * @module rhettjs/ui
 */

declare module 'rhettjs/ui' {
    /**
     * A UI screen that can contain widgets.
     * Screens are full-screen overlays that pause the game (by default).
     */
    export interface UIScreen {
        /**
         * Unique identifier for this screen (readonly).
         */
        readonly id: string;

        /**
         * Add a button widget to the screen.
         *
         * @param options Button configuration
         * @returns The widget ID
         *
         * @example
         * ```javascript
         * screen.addButton({
         *   x: 100,
         *   y: 100,
         *   width: 100,
         *   height: 20,
         *   text: 'Start Game',
         *   onClick: () => {
         *     console.log('Starting game!');
         *     screen.hide();
         *   }
         * });
         * ```
         */
        addButton(options: ButtonOptions): string;

        /**
         * Add a text label widget to the screen.
         *
         * @param options Label configuration
         * @returns The widget ID
         *
         * @example
         * ```javascript
         * screen.addLabel({
         *   x: 150,
         *   y: 50,
         *   text: 'Score: 1000',
         *   color: 0xFFD700,
         *   shadow: true
         * });
         * ```
         */
        addLabel(options: LabelOptions): string;

        /**
         * Add an image widget to the screen.
         *
         * @param options Image configuration
         * @returns The widget ID
         *
         * @example
         * ```javascript
         * screen.addImage({
         *   x: 0,
         *   y: 0,
         *   width: 256,
         *   height: 256,
         *   texture: 'rhettjs:textures/gui/background.png'
         * });
         * ```
         */
        addImage(options: ImageOptions): string;

        /**
         * Add a container panel widget to the screen.
         * Panels can have backgrounds and borders.
         *
         * @param options Panel configuration
         * @returns The widget ID
         *
         * @example
         * ```javascript
         * screen.addPanel({
         *   x: 50,
         *   y: 50,
         *   width: 200,
         *   height: 150,
         *   backgroundColor: 0xCC000000,
         *   borderColor: 0xFFFFFFFF,
         *   borderWidth: 2
         * });
         * ```
         */
        addPanel(options: PanelOptions): string;

        /**
         * Remove a widget by its ID.
         *
         * @param widgetId The ID of the widget to remove
         *
         * @example
         * ```javascript
         * const buttonId = screen.addButton({...});
         * screen.removeWidget(buttonId);
         * ```
         */
        removeWidget(widgetId: string): void;

        /**
         * Update an existing widget's properties.
         *
         * @param widgetId The ID of the widget to update
         * @param updates Object containing properties to update
         *
         * @example
         * ```javascript
         * const labelId = screen.addLabel({ x: 100, y: 100, text: 'Score: 0' });
         *
         * // Later, update the text
         * screen.updateWidget(labelId, {
         *   text: 'Score: 1000',
         *   color: 0xFFD700
         * });
         * ```
         */
        updateWidget(widgetId: string, updates: Partial<WidgetOptions>): void;

        /**
         * Show this screen to the player.
         * Makes it the active screen and pauses the game.
         */
        show(): void;

        /**
         * Hide this screen.
         * Closes the screen and returns to the game.
         */
        hide(): void;

        /**
         * Check if this screen is currently visible.
         *
         * @returns true if the screen is active
         */
        isVisible(): boolean;

        /**
         * Register an event listener for screen events.
         *
         * @param event The event name
         * @param callback Function to call when the event occurs
         *
         * @example
         * ```javascript
         * screen.on('shown', () => {
         *   console.log('Screen opened!');
         * });
         *
         * screen.on('hidden', () => {
         *   console.log('Screen closed!');
         * });
         *
         * screen.on('tick', () => {
         *   // Called 20 times per second while screen is open
         * });
         * ```
         */
        on(event: 'shown' | 'hidden' | 'tick', callback: () => void): void;
    }

    /**
     * Options for creating a button widget.
     */
    export interface ButtonOptions {
        /**
         * Optional widget ID. If not provided, a UUID will be generated.
         */
        id?: string;

        /**
         * X position in pixels from the left edge of the screen.
         */
        x: number;

        /**
         * Y position in pixels from the top edge of the screen.
         */
        y: number;

        /**
         * Button width in pixels.
         */
        width: number;

        /**
         * Button height in pixels.
         */
        height: number;

        /**
         * Button label text.
         */
        text: string;

        /**
         * Optional click handler function.
         */
        onClick?: () => void;

        /**
         * Optional custom texture (resource location).
         * If not provided, uses default Minecraft button texture.
         */
        texture?: string;
    }

    /**
     * Options for creating a label widget.
     */
    export interface LabelOptions {
        /**
         * Optional widget ID. If not provided, a UUID will be generated.
         */
        id?: string;

        /**
         * X position in pixels.
         */
        x: number;

        /**
         * Y position in pixels.
         */
        y: number;

        /**
         * Text content to display.
         * Use \n for multi-line text.
         */
        text: string;

        /**
         * Text color in RGB hex format (e.g., 0xFFFFFF for white).
         * Default: 0xFFFFFF (white)
         */
        color?: number;

        /**
         * Whether to render a drop shadow.
         * Default: true
         */
        shadow?: boolean;

        /**
         * Text scale multiplier.
         * Default: 1.0
         */
        scale?: number;
    }

    /**
     * Options for creating an image widget.
     */
    export interface ImageOptions {
        /**
         * Optional widget ID. If not provided, a UUID will be generated.
         */
        id?: string;

        /**
         * X position in pixels.
         */
        x: number;

        /**
         * Y position in pixels.
         */
        y: number;

        /**
         * Display width in pixels.
         */
        width: number;

        /**
         * Display height in pixels.
         */
        height: number;

        /**
         * Texture resource location.
         *
         * @example
         * - "minecraft:textures/block/dirt.png"
         * - "rhettjs:textures/gui/custom.png"
         */
        texture: string;

        /**
         * Texture U coordinate (horizontal offset in source texture).
         * Default: 0
         */
        u?: number;

        /**
         * Texture V coordinate (vertical offset in source texture).
         * Default: 0
         */
        v?: number;

        /**
         * Total source texture width.
         * Default: same as width
         */
        textureWidth?: number;

        /**
         * Total source texture height.
         * Default: same as height
         */
        textureHeight?: number;
    }

    /**
     * Options for creating a panel widget.
     */
    export interface PanelOptions {
        /**
         * Optional widget ID. If not provided, a UUID will be generated.
         */
        id?: string;

        /**
         * X position in pixels.
         */
        x: number;

        /**
         * Y position in pixels.
         */
        y: number;

        /**
         * Panel width in pixels.
         */
        width: number;

        /**
         * Panel height in pixels.
         */
        height: number;

        /**
         * Background color in ARGB hex format.
         * Use null for transparent background.
         *
         * @example
         * - 0xFF000000 = opaque black
         * - 0xCC333333 = semi-transparent dark gray
         * - 0x80FFFFFF = half-transparent white
         */
        backgroundColor?: number | null;

        /**
         * Border color in ARGB hex format.
         * Use null for no border.
         */
        borderColor?: number | null;

        /**
         * Border thickness in pixels.
         * Default: 1
         */
        borderWidth?: number;

        /**
         * Optional array of child widget IDs.
         * Note: Child positioning is currently not implemented.
         */
        children?: string[];
    }

    /**
     * Union type of all widget option types.
     */
    export type WidgetOptions = ButtonOptions | LabelOptions | ImageOptions | PanelOptions;

    /**
     * Main UI namespace for screen management.
     */
    export const UI: {
        /**
         * Create a new UI screen.
         *
         * @param id Unique screen identifier
         * @returns The created screen
         * @throws Error if a screen with this ID already exists
         *
         * @example
         * ```javascript
         * const mainMenu = UI.createScreen('main-menu');
         * const settings = UI.createScreen('settings');
         * ```
         */
        createScreen(id: string): UIScreen;

        /**
         * Get an existing screen by ID.
         *
         * @param id Screen identifier
         * @returns The screen, or undefined if not found
         *
         * @example
         * ```javascript
         * const screen = UI.getScreen('main-menu');
         * if (screen) {
         *   screen.show();
         * }
         * ```
         */
        getScreen(id: string): UIScreen | undefined;

        /**
         * Remove a screen by ID.
         * If the screen is currently displayed, it will be closed.
         *
         * @param id Screen identifier
         *
         * @example
         * ```javascript
         * UI.removeScreen('old-menu');
         * ```
         */
        removeScreen(id: string): void;
    };

    export default UI;
}
