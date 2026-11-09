const fs = require('fs');
const path = require('path');

// Find and fix ALL nested schema-utils packages
function findSchemaUtilsFiles(dir, fileList = [], depth = 0) {
  // Limit depth to avoid infinite recursion, but go deep enough
  if (depth > 10) return fileList;

  try {
    const files = fs.readdirSync(dir);

    files.forEach(file => {
      const filePath = path.join(dir, file);

      try {
        const stat = fs.statSync(filePath);

        if (stat.isDirectory()) {
          // Check if this is a schema-utils package
          if (file === 'schema-utils') {
            const validatePath = path.join(filePath, 'dist', 'validate.js');
            if (fs.existsSync(validatePath)) {
              fileList.push(validatePath);
            }
          }

          // Continue searching in node_modules directories
          if (file === 'node_modules' || depth === 0) {
            findSchemaUtilsFiles(filePath, fileList, depth + 1);
          }
        }
      } catch (err) {
        // Skip files we can't access
      }
    });
  } catch (err) {
    // Skip directories we can't access
  }

  return fileList;
}

const nodeModulesPath = path.join(__dirname, '..', 'node_modules');
const schemaUtilsFiles = findSchemaUtilsFiles(nodeModulesPath);

if (schemaUtilsFiles.length > 0) {
  console.log(`Found ${schemaUtilsFiles.length} schema-utils files to fix...`);

  schemaUtilsFiles.forEach(schemaUtilsPath => {
    try {
      let content = fs.readFileSync(schemaUtilsPath, 'utf8');
      let modified = false;

      // Fix formatMinimum keyword issue
      if (content.includes('formatMinimum')) {
        // Remove formatMinimum and formatMaximum from keywords array
        // Handle both (0, _ajvKeywords.default) and ajvKeywords() patterns
        content = content.replace(
          /\(0, _ajvKeywords\.default\)\(ajv, \['instanceof', 'formatMinimum', 'formatMaximum', 'patternRequired'\]\); \/\/ Custom keywords/g,
          `try {
  (0, _ajvKeywords.default)(ajv, ['instanceof', 'patternRequired']); // Removed formatMinimum/formatMaximum
} catch (e) {
  // Ignore if keywords fail to load
}`
        );

        // Handle direct ajvKeywords() calls (without _ajvKeywords.default)
        content = content.replace(
          /ajvKeywords\(ajv, \['instanceof', 'formatMinimum', 'formatMaximum', 'patternRequired'\]\); \/\/ Custom keywords/g,
          `try {
  ajvKeywords(ajv, ['instanceof', 'patternRequired']); // Removed formatMinimum/formatMaximum
} catch (e) {
  // Ignore if keywords fail to load
}`
        );

        // Also handle other variations with formatMinimum
        content = content.replace(
          /\(0, _ajvKeywords\.default\)\(ajv, \[[^\]]*'formatMinimum'[^\]]*\]\);/g,
          `try {
  (0, _ajvKeywords.default)(ajv, ['instanceof', 'patternRequired']);
} catch (e) {}`
        );

        content = content.replace(
          /ajvKeywords\(ajv, \[[^\]]*'formatMinimum'[^\]]*\]\);/g,
          `try {
  ajvKeywords(ajv, ['instanceof', 'patternRequired']);
} catch (e) {}`
        );

        modified = true;
      }

      if (modified) {
        fs.writeFileSync(schemaUtilsPath, content, 'utf8');
        const relativePath = path.relative(nodeModulesPath, schemaUtilsPath);
        console.log(`  ✓ Fixed: ${relativePath}`);
      }
    } catch (error) {
      console.log(`  ✗ Error fixing ${schemaUtilsPath}: ${error.message}`);
    }
  });

  console.log('All schema-utils files processed');
} else {
  console.log('No schema-utils files found to fix');
}
