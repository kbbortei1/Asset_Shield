import { ReactElement } from 'react';
import { FlatList, ListRenderItem, RefreshControl, StyleProp, View, ViewStyle } from 'react-native';
import { SafeAreaView, Edge } from 'react-native-safe-area-context';
import { colors, spacing } from '@/theme';
import { AppBackground } from './AppBackground';

export type ListScreenProps<T> = {
  data: readonly T[];
  renderItem: ListRenderItem<T>;
  keyExtractor: (item: T, index: number) => string;
  /** Sticky-less scrolling header (title, hero, filters…). */
  header?: ReactElement | null;
  /** Content below the list (secondary sections). */
  footer?: ReactElement | null;
  /** Shown in place of the list when `data` is empty. */
  empty?: ReactElement | null;
  padded?: boolean;
  edges?: Edge[];
  refreshing?: boolean;
  onRefresh?: () => void;
  contentStyle?: StyleProp<ViewStyle>;
  numColumns?: number;
  columnWrapperStyle?: StyleProp<ViewStyle>;
};

/**
 * Virtualized list frame — the FlatList counterpart to {@link Screen}.
 *
 * Use for the list-driven screens (home, properties, alerts, activity…): the
 * header/footer scroll with the rows, the list virtualizes long data, and the
 * bottom padding clears the floating QuickMenu button and tab bar so the last
 * row is never hidden behind them.
 */
export function ListScreen<T>({
  data,
  renderItem,
  keyExtractor,
  header,
  footer,
  empty,
  padded = true,
  edges = ['top', 'bottom'],
  refreshing,
  onRefresh,
  contentStyle,
  numColumns,
  columnWrapperStyle,
}: ListScreenProps<T>) {
  const pad: ViewStyle | null = padded ? { paddingHorizontal: spacing.screenPadding } : null;

  return (
    <View style={{ flex: 1 }}>
      {/* Opaque themed base + faint rotating photo — keeps each screen opaque so
          transitions don't reveal the screen underneath. */}
      <AppBackground />
      <SafeAreaView style={{ flex: 1, backgroundColor: 'transparent' }} edges={edges}>
        <FlatList
        data={data as T[]}
        renderItem={renderItem}
        keyExtractor={keyExtractor}
        ListHeaderComponent={header}
        ListFooterComponent={footer}
        ListEmptyComponent={empty}
        numColumns={numColumns}
        columnWrapperStyle={columnWrapperStyle}
        style={{ flex: 1 }}
        contentContainerStyle={[
          { paddingTop: spacing.lg, paddingBottom: spacing.xxl * 3, gap: spacing.lg },
          pad,
          contentStyle,
        ]}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          onRefresh ? (
            <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} tintColor={colors.primary} />
          ) : undefined
        }
        />
      </SafeAreaView>
    </View>
  );
}
