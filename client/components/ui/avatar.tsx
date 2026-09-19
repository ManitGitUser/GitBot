"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

type AvatarContextValue = {
  imageLoaded: boolean;
  setImageLoaded: (loaded: boolean) => void;
};

const AvatarContext = React.createContext<AvatarContextValue>({
  imageLoaded: false,
  setImageLoaded: () => {},
});

function Avatar({
  className,
  size = "default",
  children,
  ...props
}: React.ComponentProps<"span"> & {
  size?: "default" | "sm" | "lg";
}) {
  const [imageLoaded, setImageLoaded] = React.useState(false);

  return (
    <AvatarContext.Provider value={{ imageLoaded, setImageLoaded }}>
      <span
        data-slot="avatar"
        data-size={size}
        className={cn(
          "group/avatar relative flex size-8 shrink-0 overflow-hidden rounded-full select-none after:pointer-events-none after:absolute after:inset-0 after:rounded-full after:border after:border-border after:mix-blend-darken data-[size=lg]:size-10 data-[size=sm]:size-6 dark:after:mix-blend-lighten",
          className
        )}
        {...props}
      >
        {children}
      </span>
    </AvatarContext.Provider>
  );
}

function AvatarImage({
  className,
  src,
  alt,
  referrerPolicy = "no-referrer",
  onLoad,
  onError,
  ...props
}: React.ComponentProps<"img">) {
  const { setImageLoaded } = React.useContext(AvatarContext);
  const [hasError, setHasError] = React.useState(false);
  const [prevSrc, setPrevSrc] = React.useState(src);

  if (prevSrc !== src) {
    setPrevSrc(src);
    setHasError(false);
    if (!src) {
      setImageLoaded(false);
    }
  }

  if (!src || hasError) {
    return null;
  }

  return (
    /* eslint-disable-next-line @next/next/no-img-element */
    <img
      data-slot="avatar-image"
      src={src}
      alt={alt || ""}
      referrerPolicy={referrerPolicy}
      onLoad={(e) => {
        setImageLoaded(true);
        onLoad?.(e);
      }}
      onError={(e) => {
        setHasError(true);
        setImageLoaded(false);
        onError?.(e);
      }}
      className={cn(
        "aspect-square size-full rounded-full object-cover",
        className
      )}
      {...props}
    />
  );
}

function AvatarFallback({
  className,
  children,
  ...props
}: React.ComponentProps<"span">) {
  const { imageLoaded } = React.useContext(AvatarContext);

  if (imageLoaded) {
    return null;
  }

  return (
    <span
      data-slot="avatar-fallback"
      className={cn(
        "flex size-full items-center justify-center rounded-full bg-muted text-sm text-muted-foreground group-data-[size=sm]/avatar:text-xs",
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
}

function AvatarBadge({ className, ...props }: React.ComponentProps<"span">) {
  return (
    <span
      data-slot="avatar-badge"
      className={cn(
        "absolute right-0 bottom-0 z-10 inline-flex items-center justify-center rounded-full bg-primary text-primary-foreground bg-blend-color ring-2 ring-background select-none",
        "group-data-[size=sm]/avatar:size-2 group-data-[size=sm]/avatar:[&>svg]:hidden",
        "group-data-[size=default]/avatar:size-2.5 group-data-[size=default]/avatar:[&>svg]:size-2",
        "group-data-[size=lg]/avatar:size-3 group-data-[size=lg]/avatar:[&>svg]:size-2",
        className
      )}
      {...props}
    />
  );
}

function AvatarGroup({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="avatar-group"
      className={cn(
        "group/avatar-group flex -space-x-2 *:data-[slot=avatar]:ring-2 *:data-[slot=avatar]:ring-background",
        className
      )}
      {...props}
    />
  );
}

function AvatarGroupCount({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="avatar-group-count"
      className={cn(
        "relative flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-sm text-muted-foreground ring-2 ring-background group-has-data-[size=lg]/avatar-group:size-10 group-has-data-[size=sm]/avatar-group:size-6 [&>svg]:size-4 group-has-data-[size=lg]/avatar-group:[&>svg]:size-5 group-has-data-[size=sm]/avatar-group:[&>svg]:size-3",
        className
      )}
      {...props}
    />
  );
}

export {
  Avatar,
  AvatarImage,
  AvatarFallback,
  AvatarGroup,
  AvatarGroupCount,
  AvatarBadge,
};
