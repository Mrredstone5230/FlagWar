
/*
 * Copyright 2021 TownyAdvanced
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.palmergames.bukkit.towny.war.flagwar.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.palmergames.bukkit.towny.war.flagwar.CellUnderAttack;

public class CellWonEvent extends Event implements Cancellable {

	private static final HandlerList handlers = new HandlerList();
	public boolean cancelled = false;

	@Override
	public HandlerList getHandlers() {

		return handlers;
	}

	public static HandlerList getHandlerList() {

		return handlers;
	}

	//////////////////////////////

	private CellUnderAttack cellAttackData;

	public CellWonEvent(CellUnderAttack cellAttackData) {

		super();
		this.cellAttackData = cellAttackData;
	}

	public CellUnderAttack getCellAttackData() {

		return cellAttackData;
	}
	
	@Override
    public boolean isCancelled() {
        return cancelled;
    }
	
     @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}