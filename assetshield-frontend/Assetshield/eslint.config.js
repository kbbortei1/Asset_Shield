// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/*'],
  },
  {
    rules: {
      // React Native <Text> renders raw strings — HTML entity escaping is a web concern.
      'react/no-unescaped-entities': 'off',
    },
  },
]);
