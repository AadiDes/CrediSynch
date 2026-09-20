import { createSlice } from '@reduxjs/toolkit';

const initialState = {
  username: null,
  roles: [],
  authLevel: null,
};

const sessionSlice = createSlice({
  name: 'session',
  initialState,
  reducers: {
    sessionEstablished: (state, action) => {
      state.username = action.payload.username;
      state.roles = action.payload.roles;
      state.authLevel = action.payload.authLevel ?? null;
    },
    sessionCleared: () => initialState,
  },
});

export const { sessionEstablished, sessionCleared } = sessionSlice.actions;
export const selectSession = (state) => state.session;
export const selectHasRole = (role) => (state) => state.session.roles.includes(role);
export default sessionSlice.reducer;
