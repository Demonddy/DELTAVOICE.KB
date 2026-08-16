import { useEffect, useState } from "react";
import {
  getPlatformInfo,
  getPlatformInfoSync,
  type PlatformInfo,
} from "../utils/platform";

export function usePlatformInfo(): PlatformInfo {
  const [info, setInfo] = useState<PlatformInfo>(() => getPlatformInfoSync());

  useEffect(() => {
    getPlatformInfo().then(setInfo);
  }, []);

  return info;
}
