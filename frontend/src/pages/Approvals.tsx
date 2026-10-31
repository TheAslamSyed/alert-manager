import FixesPanel from '../components/FixesPanel';
import { PageHeader } from '../components/ui';

/** FR-08: every fix waiting for a human decision, across incidents. */
export default function Approvals({ roles, user }: { roles: string[]; user: string }) {
  return (
    <>
      <PageHeader title="Approval queue"
        subtitle="Agents only propose. A responder sends a fix for approval; a different approver (never an admin) lets it run. High risk needs two approvers." />
      <FixesPanel roles={roles} user={user} />
    </>
  );
}
