package com.gdu.zeus.ops.workorder.client;


import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.services.PatrolOrderService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/")
public class PatrolController {

	private final PatrolOrderService patrolOrderService;

	public PatrolController(PatrolOrderService patrolOrderService) {
		this.patrolOrderService = patrolOrderService;
	}
	@RequestMapping("/")
	public String index() {
		return "index";
	}

	@RequestMapping("/api/bookings")
	@ResponseBody
	public List<PatrolOrder> getBookings() {
		return patrolOrderService.queryPatrolOrder();
	}

}
