import * as SecureStore from 'expo-secure-store';
import { StatusBar } from 'expo-status-bar';
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { View } from 'react-native';
import { applyThemeColors, ThemeName } from '@/theme';

const STORAGE_KEY = 'as_theme';

type ThemeContextValue = {
  theme: ThemeName;
  isDark: boolean;
  setTheme: (t: ThemeName) => void;
};

const ThemeContext = createContext<ThemeContextValue>({ theme: 'light', isDark: false, setTheme: () => {} });

/**
 * Applies the selected palette to the live `colors` object and remounts the
 * visual subtree (via a key) so every screen re-reads the new colors. Placed
 * BELOW the data providers so React Query cache / session survive a theme swap
 * (nav resets to its initial route, which the auth gate resolves back to home).
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>('light');
  const [version, setVersion] = useState(0);

  useEffect(() => {
    (async () => {
      const saved = (await SecureStore.getItemAsync(STORAGE_KEY)) as ThemeName | null;
      if (saved && saved !== 'light') {
        applyThemeColors(saved);
        setThemeState(saved);
        setVersion((v) => v + 1);
      }
    })();
  }, []);

  const setTheme = useCallback((t: ThemeName) => {
    applyThemeColors(t);
    setThemeState(t);
    setVersion((v) => v + 1);
    SecureStore.setItemAsync(STORAGE_KEY, t).catch(() => {});
  }, []);

  const isDark = theme !== 'light';

  return (
    <ThemeContext.Provider value={{ theme, isDark, setTheme }}>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <View key={version} style={{ flex: 1 }}>
        {children}
      </View>
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}
