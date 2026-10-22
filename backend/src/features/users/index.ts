import type { AuthProvider } from '../../core/auth/auth-provider.js'
import type { Database } from '../../core/db/database.js'
import type { Feature } from '../../core/http/app-env.js'
import { UsersRepository } from './users.repository.js'
import { usersRoutes } from './users.routes.js'
import { type UsersPolicy, UsersService } from './users.service.js'

export function createUsersFeature(deps: {
  db: Database
  auth: AuthProvider
  onAccountDeleted?: (userId: string) => Promise<void>
  policy?: UsersPolicy
}) {
  const service = new UsersService(
    new UsersRepository(deps.db),
    deps.auth,
    deps.onAccountDeleted,
    undefined,
    deps.policy,
  )
  const feature: Feature = { name: 'users', routes: usersRoutes(service) }
  return { feature, service }
}
