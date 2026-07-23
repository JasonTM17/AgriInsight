export default function HomePage() {
  return (
    <section>
      <h1>Server-held OIDC session evidence</h1>
      <p>No provider token is rendered or stored by this browser application.</p>
      <p>
        <a href="/api/auth/login?returnTo=/protected">Sign in through the demo issuer</a>
      </p>
      <p>
        <a href="/protected">Open the protected route</a>
      </p>
    </section>
  );
}
