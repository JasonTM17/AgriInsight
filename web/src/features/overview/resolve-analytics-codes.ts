export type OperationalFarm = Readonly<{
  id: string;
  code: string;
  displayName: string;
  active: boolean;
  version: number;
}>;

export class ScopeResolutionError extends Error {
  constructor(
    public readonly reason: "inactive" | "unknown",
    message: string
  ) {
    super(message);
    this.name = "ScopeResolutionError";
  }
}

export function resolveFarmCode(
  farms: readonly OperationalFarm[],
  farmId: string
): OperationalFarm {
  const farm = farms.find((candidate) => candidate.id === farmId);
  if (!farm) {
    throw new ScopeResolutionError(
      "unknown",
      "Không tìm thấy nông trại trong phạm vi được cấp quyền."
    );
  }
  if (!farm.active) {
    throw new ScopeResolutionError(
      "inactive",
      "Nông trại đã ngừng hoạt động và không thể mở rộng phạm vi phân tích."
    );
  }
  return farm;
}

export function mergeFarmAnalyticsByCode<AnalyticsFarm extends { readonly farmCode: string }>(
  farms: readonly OperationalFarm[],
  analytics: readonly AnalyticsFarm[]
): readonly Readonly<{ farm: OperationalFarm; analytics: AnalyticsFarm | null }>[] {
  const analyticsByCode = new Map(analytics.map((item) => [item.farmCode, item]));
  return farms.map((farm) => ({
    farm,
    analytics: analyticsByCode.get(farm.code) ?? null
  }));
}
