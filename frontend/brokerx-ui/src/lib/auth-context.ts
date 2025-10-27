import { createContext } from "react";

export type AuthCtx = { jwt: string | null; setJwt: (t: string | null) => void; logout: () => void };

export const AuthContext = createContext<AuthCtx>({ jwt: null, setJwt: () => {}, logout: () => {} });