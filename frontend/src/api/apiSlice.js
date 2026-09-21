import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import keycloak from '../keycloak';

/**
 * One RTK Query client for the whole app. Every request carries the bearer token
 * from the live Keycloak session; the token is never copied into Redux state.
 */
export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({
    baseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
    prepareHeaders: (headers) => {
      if (keycloak.token) {
        headers.set('Authorization', `Bearer ${keycloak.token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Case', 'Decision'],
  endpoints: (builder) => ({
    getMe: builder.query({ query: () => '/v1/me' }),
    getQueueSummary: builder.query({ query: () => '/v1/queue/summary', providesTags: ['Case'] }),
    getPlatformStatus: builder.query({ query: () => '/v1/platform/status' }),
    getCases: builder.query({
      query: ({ status, cursor, limit } = {}) => ({
        url: '/v1/cases',
        params: { status: status || undefined, cursor: cursor || undefined, limit },
      }),
      providesTags: ['Case'],
    }),
    getCaseDetail: builder.query({
      query: (caseId) => `/v1/cases/${caseId}`,
      providesTags: (result, error, caseId) => [{ type: 'Case', id: caseId }],
    }),
    postCaseLabel: builder.mutation({
      query: ({ caseId, label, note }) => ({
        url: `/v1/cases/${caseId}/labels`,
        method: 'POST',
        body: { label, note: note || undefined },
      }),
      invalidatesTags: (result, error, { caseId }) => [{ type: 'Case', id: caseId }, 'Case'],
    }),
  }),
});

export const {
  useGetMeQuery,
  useGetQueueSummaryQuery,
  useGetPlatformStatusQuery,
  useGetCasesQuery,
  useGetCaseDetailQuery,
  usePostCaseLabelMutation,
} = apiSlice;
