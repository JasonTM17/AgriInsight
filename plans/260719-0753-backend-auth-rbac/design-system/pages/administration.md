# Administration page override

Primary job: manage tenant users, fixed roles, and farm/warehouse/activity assignments with an audit trail.

- The backend remains the authorization boundary. Navigation visibility is advisory; every deep link handles server-side 403/404.
- Use explicit role/permission descriptions, assignment scope chips with text, confirmation for deactivation, and optimistic-version conflict recovery.
- Never display secrets, tokens, raw OIDC claims, or another tenant's existence. Audit entries show actor, action, target, scope, and time.
- Mobile: use a step-indicated assignment flow with back/cancel and unsaved-change protection.
