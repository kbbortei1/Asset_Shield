import { Ionicons } from '@expo/vector-icons';
import { Component, ReactNode } from 'react';
import { Pressable, Text, View } from 'react-native';
import { colors } from '@/theme';

type Props = { children: ReactNode };
type State = { error: Error | null };

/**
 * Last-line-of-defence crash screen. Without this, any uncaught render error
 * white-screens the whole app with no way back. Sits ABOVE all providers, so
 * it must not depend on theme context, fonts or navigation — plain RN views
 * and the always-populated mutable `colors` object only.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: { componentStack?: string | null }) {
    // Surfaces in Metro/adb logcat today; swap for Sentry.captureException later.
    console.error('[ErrorBoundary]', error, info.componentStack ?? '');
  }

  private reset = () => this.setState({ error: null });

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <View style={{ flex: 1, backgroundColor: colors.background, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 16 }}>
        <Ionicons name="shield-half" size={56} color={colors.primary} />
        <Text style={{ fontSize: 20, fontWeight: '700', color: colors.text, textAlign: 'center' }}>
          Something went wrong
        </Text>
        <Text style={{ fontSize: 14, color: colors.textMuted, textAlign: 'center' }}>
          An unexpected error occurred. Your documented assets and evidence are safe.
        </Text>
        <Pressable
          onPress={this.reset}
          style={({ pressed }) => ({
            backgroundColor: colors.primary,
            paddingHorizontal: 28,
            paddingVertical: 14,
            borderRadius: 12,
            opacity: pressed ? 0.85 : 1,
          })}
        >
          <Text style={{ color: colors.onPrimary, fontWeight: '600', fontSize: 15 }}>Try again</Text>
        </Pressable>
        {__DEV__ ? (
          <Text style={{ fontSize: 12, color: colors.textMuted, textAlign: 'center' }} numberOfLines={4}>
            {String(this.state.error.message)}
          </Text>
        ) : null}
      </View>
    );
  }
}
