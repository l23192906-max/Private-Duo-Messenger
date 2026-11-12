import { Router, type IRouter } from "express";
import healthRouter from "./health";
import ourChatRouter from "./our-chat";

const router: IRouter = Router();

router.use(healthRouter);
router.use(ourChatRouter);

export default router;
