import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTheme } from "@/lib/theme";

export function ThemeToggle() {
  const { resolved, toggle } = useTheme();
  const next = resolved === "dark" ? "light" : "dark";
  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label={`Switch to ${next} mode`} data-testid="theme-toggle">
      {resolved === "dark" ? <Sun /> : <Moon />}
    </Button>
  );
}
