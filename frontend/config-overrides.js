// Webpack configuration to fix Node.js module issues in browser
module.exports = function override(config, env) {
  // Add fallbacks for Node.js modules that aren't available in browser
  config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    "net": false,
    "tls": false,
    "fs": false,
    "path": false,
    "crypto": false,
    "stream": false,
    "util": false,
    "buffer": false,
    "process": false,
    "os": false,
    "http": false,
    "https": false,
    "zlib": false,
    "url": false,
    "querystring": false,
  };

  // Ignore Node.js-specific files in stompjs
  config.module.rules.push({
    test: /stompjs[\\/]lib[\\/]stomp-node\.js$/,
    use: 'null-loader'
  });

  // Completely disable ESLint plugin to fix html-webpack-plugin errors
  config.plugins = config.plugins.filter(
    plugin => plugin.constructor.name !== 'ESLintWebpackPlugin'
  );

  return config;
};
