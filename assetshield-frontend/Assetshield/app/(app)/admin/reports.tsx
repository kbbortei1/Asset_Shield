import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { ProblemReport, ReportCategory, usersApi } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, Header, ListScreen, Loading, StatusBadge, Text, useToast } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const CATEGORY_LABEL: Record<ReportCategory, string> = {
  BUG: 'Bug',
  PAYMENT: 'Payment',
  ACCOUNT: 'Account',
  SUGGESTION: 'Suggestion',
  OTHER: 'Other',
};

/** Admin: triage user-filed problem reports. */
export default function AdminReports() {
  const qc = useQueryClient();
  const { show } = useToast();
  const [filter, setFilter] = useState<'OPEN' | 'ALL'>('OPEN');

  const q = useQuery({
    queryKey: ['admin-reports', filter],
    queryFn: () => usersApi.adminReports({ size: 100, status: filter === 'OPEN' ? 'OPEN' : undefined }),
  });

  const resolve = useMutation({
    mutationFn: (id: string) => usersApi.resolveReport(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-reports'] });
      show('Marked resolved');
    },
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const items = q.data?.items ?? [];

  const header = (
    <View style={{ gap: spacing.md }}>
      <Header title="Problem reports" />
      <View style={{ flexDirection: 'row', gap: spacing.sm }}>
        {(['OPEN', 'ALL'] as const).map((f) => {
          const active = filter === f;
          return (
            <Pressable
              key={f}
              onPress={() => setFilter(f)}
              style={{
                paddingHorizontal: spacing.lg,
                paddingVertical: spacing.sm,
                borderRadius: radius.xl,
                borderWidth: 1,
                borderColor: active ? colors.primary : colors.border,
                backgroundColor: active ? colors.primary : 'transparent',
              }}
            >
              <Text variant="labelMd" weight="semibold" color={active ? colors.onPrimary : colors.textMuted}>
                {f === 'OPEN' ? 'Open' : 'All'}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );

  return (
    <ListScreen
      data={items}
      keyExtractor={(r) => r.id}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={header}
      renderItem={({ item }) => <ReportCard report={item} onResolve={() => resolve.mutate(item.id)} busy={resolve.isPending} />}
      empty={
        <EmptyState
          icon="chatbox-ellipses-outline"
          title={filter === 'OPEN' ? 'No open reports' : 'No reports yet'}
          body="When users report a problem in the app, it shows up here."
        />
      }
    />
  );
}

function ReportCard({ report, onResolve, busy }: { report: ProblemReport; onResolve: () => void; busy: boolean }) {
  const open = report.status === 'OPEN';
  return (
    <Card>
      <View style={{ gap: spacing.sm }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
          <View style={{ backgroundColor: colors.tealTint, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 2 }}>
            <Text variant="labelMd" weight="semibold" color={colors.primary}>
              {CATEGORY_LABEL[report.category]}
            </Text>
          </View>
          <View style={{ flex: 1 }} />
          <StatusBadge status={open ? 'needsUpdate' : 'secured'} label={report.status} />
        </View>

        <Text variant="bodyMd">{report.message}</Text>

        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Ionicons name="person-outline" size={13} color={colors.textMuted} />
          <Text variant="labelMd" color={colors.textMuted}>
            {report.reporterName ?? 'User'}
            {report.reporterPhone ? ` · ${report.reporterPhone}` : ''}
            {report.context ? ` · ${report.context}` : ''} · {new Date(report.createdAt).toLocaleString()}
          </Text>
        </View>

        {open ? (
          <Button title="Mark resolved" fullWidth={false} variant="secondary" loading={busy} onPress={onResolve} />
        ) : null}
      </View>
    </Card>
  );
}
