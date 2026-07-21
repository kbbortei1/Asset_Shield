import {
  Inter_400Regular,
  Inter_500Medium,
  Inter_600SemiBold,
  Inter_700Bold,
} from '@expo-google-fonts/inter';
import { Sora_600SemiBold, Sora_700Bold, useFonts } from '@expo-google-fonts/sora';
import { DefaultTheme, ThemeProvider as NavThemeProvider } from '@react-navigation/native';
import { QueryClientProvider } from '@tanstack/react-query';
import { Asset } from 'expo-asset';
import { Stack, useRouter, useSegments } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { KeyboardProvider } from 'react-native-keyboard-controller';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { ConfirmProvider, ImageViewerProvider, ToastProvider } from '@/components/ui';
import { AuthProvider, useAuth } from '@/lib/auth/AuthProvider';
import { OfflineProvider } from '@/lib/offline/OfflineProvider';
import { ThemeProvider } from '@/lib/theme/ThemeProvider';
import { queryClient } from '@/lib/query';
import { colors } from '@/theme';

SplashScreen.preventAutoHideAsync();

// Warm the brand/backdrop images at boot. In Expo Go dev, require()d assets
// stream from Metro over the LAN per first use, so on a weak network the logo
// pops in late; preloading pulls them once, up front. (Production builds bundle
// them, making this a fast no-op.) Fire-and-forget: failures just mean the
// old lazy behavior.
Asset.loadAsync([
  require('@/assets/images/logo-emblem.png'),
  require('@/assets/images/background1.jpg'),
  require('@/assets/images/background2.jpg'),
  require('@/assets/images/background3.jpg'),
  require('@/assets/images/background4.jpg'),
]).catch(() => {});

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    Sora_600SemiBold,
    Sora_700Bold,
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    Inter_700Bold,
  });

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <ErrorBoundary>
        <KeyboardProvider>
          <SafeAreaProvider>
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <OfflineProvider>
                <ThemeProvider>
                  <ImageViewerProvider>
                    <ToastProvider>
                      <ConfirmProvider>
                        <RootNav fontsLoaded={fontsLoaded} />
                      </ConfirmProvider>
                    </ToastProvider>
                  </ImageViewerProvider>
                </ThemeProvider>
              </OfflineProvider>
            </AuthProvider>
          </QueryClientProvider>
          </SafeAreaProvider>
        </KeyboardProvider>
      </ErrorBoundary>
    </GestureHandlerRootView>
  );
}

function RootNav({ fontsLoaded }: { fontsLoaded: boolean }) {
  const { status, pendingOnboarding } = useAuth();
  useProtectedRoute(status, pendingOnboarding);

  useEffect(() => {
    if (fontsLoaded && status !== 'loading') {
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, status]);

  if (!fontsLoaded) return null;

  // Navigation theme uses our themed base (not React Navigation's default light),
  // so scene containers paint the correct dark/gold/light field. Each Screen also
  // paints its own opaque base, so group transitions never reveal the screen behind.
  const navTheme = {
    ...DefaultTheme,
    colors: { ...DefaultTheme.colors, background: colors.background, card: colors.background },
  };

  return (
    <NavThemeProvider value={navTheme}>
      <View style={{ flex: 1, backgroundColor: colors.background }}>
        <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
          <Stack.Screen name="(auth)" />
          <Stack.Screen name="(app)" />
        </Stack>
      </View>
    </NavThemeProvider>
  );
}

/** Redirect between the auth and app route groups based on session status. */
function useProtectedRoute(status: ReturnType<typeof useAuth>['status'], pendingOnboarding: boolean) {
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (status === 'loading') return;
    const seg = segments as string[];
    const inAuthGroup = seg[0] === '(auth)';

    if (status === 'unauthenticated' && !inAuthGroup) {
      router.replace('/(auth)/splash' as never);
    } else if (status === 'authenticated' && (inAuthGroup || seg.length === 0)) {
      router.replace((pendingOnboarding ? '/(app)/kyc?onboarding=1' : '/(app)/(tabs)/home') as never);
    }
  }, [status, pendingOnboarding, segments, router]);
}
