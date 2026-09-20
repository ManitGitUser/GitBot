"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";


import { api } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { toast } from "@/components/ui/toast";

export const AUTH_COOKIE = "gitbot_auth";

export function setAuthCookie(authed: boolean) {
    if (typeof document === "undefined") return;
    if (authed) {
        document.cookie = `${AUTH_COOKIE}=1; path=/; max-age=${60 * 60 * 24 * 7}; SameSite=Lax`;
    } else {
        document.cookie = `${AUTH_COOKIE}=; path=/; max-age=0; SameSite=Lax`;
    }
}


export function useCurrentUser(){
    return useQuery({
        queryKey: queryKeys.auth.me(),
        queryFn: async()=>{
            try {
                const user = await api.me();
                setAuthCookie(true);
                return user;
            } catch (error) {
                setAuthCookie(false);
                throw error;
            }

        },
        staleTime: 5 * 60 * 1000,
        retry: false,
    })
}

export function useLogout() {
    const queryClient = useQueryClient();
    const router = useRouter();

    return useMutation({
        mutationFn: () => api.logout(),
        onSettled: async () => {
            setAuthCookie(false);
            queryClient.setQueryData(queryKeys.auth.me(), null);
            await queryClient.invalidateQueries({ queryKey: queryKeys.auth.all });
            router.replace("/login");
        },
    });
}

export function useSyncProfile() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: () => api.syncProfile(),
        onSuccess: (user) => {
            queryClient.setQueryData(queryKeys.auth.me(), user);
            toast.add({
                title: "Profile synced",
                description: "Your GitHub profile details have been refreshed.",
                type: "success",
            });
        },
        onError: (error: Error) => {
            toast.add({
                title: "Unable to sync profile",
                description: error.message,
                type: "error",
            });
        },
    });
}

export function useDeleteAccount() {
    const queryClient = useQueryClient();
    const router = useRouter();

    return useMutation({
        mutationFn: () => api.deleteAccount(),
        onSuccess: async () => {
            setAuthCookie(false);
            queryClient.clear();
            toast.add({
                title: "Account deleted",
                description: "Your account and all associated GitBot data have been permanently removed.",
                type: "success",
            });
            router.replace("/");
        },
        onError: (error: Error) => {
            toast.add({
                title: "Unable to delete account",
                description: error.message || "An error occurred while deleting your account. Please try again.",
                type: "error",
            });
        },
    });
}