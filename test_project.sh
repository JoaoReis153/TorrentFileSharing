#!/bin/bash
sed -i -e 's/\r$//' run_project.sh

PROJECT_DIR="./Code"
SRC_DIR="$PROJECT_DIR/src"
BIN_DIR="$PROJECT_DIR/bin"
SOURCE_FILE_STRUCTURE="files"
TARGET_FILE_STRUCTURE="$PROJECT_DIR/files"

MAIN_CLASS="Tests.Test"

# Verify source file structure exists
if [ ! -d "$SOURCE_FILE_STRUCTURE" ]; then
  echo "Default file structure not found: $SOURCE_FILE_STRUCTURE"
  exit 1
fi

# Ensure target file structure exists
if [ ! -d "$TARGET_FILE_STRUCTURE" ]; then
  mkdir -p "$TARGET_FILE_STRUCTURE"
  if [ $? -ne 0 ]; then
    echo "Failed to create file structure: $TARGET_FILE_STRUCTURE"
    exit 1
  fi
fi

# Copy files/dl* into Code/files/dl*
for source_dir in "$SOURCE_FILE_STRUCTURE"/dl*; do
  [ -d "$source_dir" ] || continue
  target_dir="$TARGET_FILE_STRUCTURE/$(basename "$source_dir")"
  rm -rf "$target_dir"
  cp -r "$source_dir" "$target_dir"
  if [ $? -ne 0 ]; then
    echo "Failed to copy files from $source_dir to $target_dir"
    exit 1
  fi
done

# Compile Java files
javac -d "$BIN_DIR" -sourcepath "$SRC_DIR" $(find "$SRC_DIR" -name "*.java")
if [ $? -ne 0 ]; then
  echo "Compilation failed!"
  exit 1
fi

# Run the Java main class
java -cp "$BIN_DIR" "$MAIN_CLASS" "$@"
