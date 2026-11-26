import * as React from "react";
import { Tooltip as Primitive } from "radix-ui";
import { cn } from "@/lib/utils";

export const TooltipProvider = Primitive.Provider;
export const Tooltip = Primitive.Root;
export const TooltipTrigger = Primitive.Trigger;

export function TooltipContent({ className, sideOffset = 4, ...props }: React.ComponentProps<typeof Primitive.Content>) {
  return (
    <Primitive.Portal>
      <Primitive.Content
        sideOffset={sideOffset}
        className={cn("z-50 max-w-xs rounded-md border bg-popover px-3 py-1.5 text-xs text-popover-foreground shadow-md", className)}
        {...props}
      />
    </Primitive.Portal>
  );
}
