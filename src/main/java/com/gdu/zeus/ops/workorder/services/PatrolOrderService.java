package com.gdu.zeus.ops.workorder.services;
import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.repository.PatrolOrderRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PatrolOrderService {

    @Autowired
    private PatrolOrderRepository repository;

    public PatrolOrder createOrder(PatrolOrder order) {
        return repository.save(order);
    }

    public List<PatrolOrder> queryPatrolOrder(){
        List<PatrolOrder> list = repository.findAll();
        list = list.stream().map(patrolOrder->{
            PatrolOrder order = new PatrolOrder();
            BeanUtils.copyProperties(patrolOrder,order);
            order.setPatrolResultDesc(patrolOrder.getPatrolResult().getDescription());
            order.setExecutionTypeDesc(patrolOrder.getExecutionType().getDescription());
            order.setStatusDesc(patrolOrder.getStatus().getDescription());
            return order;
        }).collect(Collectors.toList());
        return list;
    }
}
