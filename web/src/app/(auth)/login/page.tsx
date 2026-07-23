type LoginPageProps = Readonly<{
  searchParams: Promise<{ returnTo?: string }>;
}>;

export const dynamic = "force-dynamic";

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const { returnTo } = await searchParams;
  const target = new URLSearchParams({ returnTo: returnTo ?? "/protected" });
  return (
    <section className="foundation-panel foundation-panel--narrow">
      <p className="eyebrow">Xác thực doanh nghiệp</p>
      <h1>Đăng nhập an toàn</h1>
      <p>
        AgriInsight sử dụng Authorization Code + PKCE. Quyền truy cập được đọc
        mới từ backend cho từng yêu cầu bảo vệ.
      </p>
      <a className="primary-action" href={`/api/auth/login?${target}`}>
        Tiếp tục với nhà cung cấp OIDC
      </a>
    </section>
  );
}
