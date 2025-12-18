#!/bin/bash
# Setup symlinks from run directories to rjs-test-scripts

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_SCRIPTS_DIR="$SCRIPT_DIR/rjs-test-scripts"

echo "Setting up RhettJS test scripts symlinks..."

# Function to setup symlink for a loader
setup_symlink() {
    local loader=$1
    local run_dir="$SCRIPT_DIR/$loader/versions/1.21.1/run"

    if [ -d "$run_dir" ]; then
        echo "  Setting up $loader..."

        # Remove existing rjs directory if it exists
        if [ -e "$run_dir/rjs" ]; then
            if [ -L "$run_dir/rjs" ]; then
                echo "    Removing existing symlink"
            else
                echo "    WARNING: $run_dir/rjs exists and is not a symlink"
                echo "    Backing up to $run_dir/rjs.backup"
                mv "$run_dir/rjs" "$run_dir/rjs.backup"
            fi
            rm -f "$run_dir/rjs"
        fi

        # Create symlink
        ln -s ../../../../rjs-test-scripts "$run_dir/rjs"
        echo "    ✓ Created symlink: $run_dir/rjs -> rjs-test-scripts"
    else
        echo "  $loader run directory doesn't exist yet (will be created on first run)"
    fi
}

# Setup for Fabric
setup_symlink "fabric"

# Setup for NeoForge
setup_symlink "neoforge"

echo ""
echo "✓ Setup complete!"
echo ""
echo "Test scripts location: $TEST_SCRIPTS_DIR"
echo "Edit scripts there, they'll be available in both Fabric and NeoForge runs."
