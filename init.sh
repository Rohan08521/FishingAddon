#!/bin/bash
set -e


PACKAGE=$1
PROJECT=$2
MODID=$3
ROOT=$(pwd)
PACKAGE_PATH=$(echo "$PACKAGE" | tr '.' '/')

echo "Working directory: $ROOT"
echo "Package path: $PACKAGE_PATH"

echo ""
echo "Renaming files..."

find "$ROOT" -type f -name "Example*.kt" | while read -r file; do
    dir=$(dirname "$file")
    filename=$(basename "$file")
    newname=$(echo "$filename" | sed "s/Example/$PROJECT/g")
    if [ "$filename" != "$newname" ]; then
        mv "$file" "$dir/$newname"
        echo " Renamed $filename → $newname"
    fi
done


find "$ROOT" -type f -name "Example*.java" | while read -r file; do
    dir=$(dirname "$file")
    filename=$(basename "$file")
    newname=$(echo "$filename" | sed "s/Example/$PROJECT/g")
    if [ "$filename" != "$newname" ]; then
        mv "$file" "$dir/$newname"
        echo " Renamed $filename → $newname"
    fi
done


echo ""
echo "Replacing identifiers across project..."

find "$ROOT" -type f \( \
    -name "*.java" -o \
    -name "*.kt" -o \
    -name "*.json" -o \
    -name "*.properties" -o \
    -name "*.gradle" -o \
    -name "*.kts" -o \
    -name "*.xml" -o \
    -name "*.toml" -o \
    -name "*.yml" -o \
    -name "*.yaml" -o \
    -name "*.md" \
\) ! -path "*/.git/*" ! -path "*/build/*" | while read -r file; do
    sed -i \
        -e "s/com\.example/$PACKAGE/g" \
        -e "s/exampleaddon/$MODID/g" \
        -e "s/ExampleAddon/$PROJECT/g" \
        -e "s/Example/$PROJECT/g" \
        "$file"
done


if [ -d "$ROOT/src/main/java/com/example" ]; then
    mkdir -p "$ROOT/src/main/java/$PACKAGE_PATH"
    
    cp -r "$ROOT/src/main/java/com/example/"* "$ROOT/src/main/java/$PACKAGE_PATH/" 2>/dev/null || true
    if [ -d "$ROOT/src/main/java/$PACKAGE_PATH" ]; then
        rm -rf "$ROOT/src/main/java/com"
        echo "Java sources moved to $PACKAGE_PATH"
    fi
else
    echo " No Java sources found at com/example"
fi


echo ""
echo "Restructuring Kotlin source directories..."

if [ -d "$ROOT/src/main/kotlin/com/example" ]; then
    mkdir -p "$ROOT/src/main/kotlin/$PACKAGE_PATH"
    
    cp -r "$ROOT/src/main/kotlin/com/example/"* "$ROOT/src/main/kotlin/$PACKAGE_PATH/" 2>/dev/null || true
    
    if [ -d "$ROOT/src/main/kotlin/$PACKAGE_PATH" ]; then
        rm -rf "$ROOT/src/main/kotlin/com"
        echo "Kotlin sources moved to $PACKAGE_PATH"
    fi
else
    echo " No Kotlin sources found at com/example"
fi

echo ""
echo "Renaming mixin configuration files..."

if [ -f "$ROOT/src/main/resources/exampleaddon.mixins.json" ]; then
    mv "$ROOT/src/main/resources/exampleaddon.mixins.json" \
       "$ROOT/src/main/resources/$MODID.mixins.json"
    echo "✓ Renamed exampleaddon.mixins.json → $MODID.mixins.json"
fi

if [ -f "$ROOT/src/main/resources/mixins.exampleaddon.json" ]; then
    mv "$ROOT/src/main/resources/mixins.exampleaddon.json" \
       "$ROOT/src/main/resources/mixins.$MODID.json"
    echo "✓ Renamed mixins.exampleaddon.json → mixins.$MODID.json"
fi

if [ -f "$ROOT/src/main/resources/mixins.ExampleAddon.json" ]; then
    mv "$ROOT/src/main/resources/mixins.ExampleAddon.json" \
       "$ROOT/src/main/resources/mixins.$MODID.json"
    echo "✓ Renamed mixins.ExampleAddon.json → mixins.$MODID.json"
fi

if [ -f "$ROOT/src/main/resources/ExampleAddon.mixins.json" ]; then
    mv "$ROOT/src/main/resources/ExampleAddon.mixins.json" \
       "$ROOT/src/main/resources/$PROJECT.mixins.json"
    echo "✓ Renamed ExampleAddon.mixins.json → $PROJECT.mixins.json"
fi

echo ""
echo "Updating fabric.mod.json..."

if [ -f "$ROOT/src/main/resources/fabric.mod.json" ]; then
    sed -i \
        -e "s/exampleaddon/$MODID/g" \
        -e "s/ExampleAddon/$PROJECT/g" \
        -e "s/com\.example/$PACKAGE/g" \
        "$ROOT/src/main/resources/fabric.mod.json"
    echo "Updated fabric.mod.json"
else
    echo " fabric.mod.json not found"
fi

echo ""
echo "Renaming assets directories..."

if [ -d "$ROOT/src/main/resources/assets/exampleaddon" ]; then
    mv "$ROOT/src/main/resources/assets/exampleaddon" \
       "$ROOT/src/main/resources/assets/$MODID"
    echo "✓ Renamed assets/exampleaddon → assets/$MODID"
fi

if [ -d "$ROOT/src/main/resources/assets/ExampleAddon" ]; then
    mv "$ROOT/src/main/resources/assets/ExampleAddon" \
       "$ROOT/src/main/resources/assets/$MODID"
    echo "✓ Renamed assets/ExampleAddon → assets/$MODID"
fi

echo ""
echo "Updating Gradle configuration..."

if [ -f "$ROOT/settings.gradle.kts" ]; then
    sed -i \
        -e "s/exampleaddon/$MODID/g" \
        -e "s/ExampleAddon/$PROJECT/g" \
        "$ROOT/settings.gradle.kts"
    echo "✓ Updated settings.gradle.kts"
fi

if [ -f "$ROOT/gradle.properties" ]; then
    sed -i \
        -e "s/exampleaddon/$MODID/g" \
        -e "s/ExampleAddon/$PROJECT/g" \
        "$ROOT/gradle.properties"
    echo "✓ Updated gradle.properties"
fi

if [ -f "$ROOT/build.gradle.kts" ]; then
    sed -i \
        -e "s/exampleaddon/$MODID/g" \
        -e "s/ExampleAddon/$PROJECT/g" \
        -e "s/com\.example/$PACKAGE/g" \
        "$ROOT/build.gradle.kts"
    echo "✓ Updated build.gradle.kts"
fi

echo ""
echo "Cleaning up template files..."

if [ -f "$ROOT/.github/workflows/InitTemplate.yaml" ]; then
    rm -f "$ROOT/.github/workflows/InitTemplate.yaml"
    echo "✓ Removed InitTemplate.yaml"
fi

if [ -f "$ROOT/.github/workflows/InitTemplate.yml" ]; then
    rm -f "$ROOT/.github/workflows/InitTemplate.yml"
    echo "✓ Removed InitTemplate.yml"
fi

SCRIPT_PATH=$(readlink -f "$0" 2>/dev/null || realpath "$0" 2>/dev/null || echo "$0")
if [ -f "$SCRIPT_PATH" ]; then
    rm -f "$SCRIPT_PATH"
    echo " Removed init.sh"
fi

echo ""
echo "=========================================="
echo " Template initialization complete!"
echo "=========================================="
echo "Your project is ready:"
echo "  • Package: $PACKAGE"
echo "  • Project: $PROJECT"
echo "  • Mod ID: $MODID"
echo "=========================================="
