import React, {type ReactNode} from 'react';
import {
  useDocById,
  findFirstSidebarItemLink,
} from '@docusaurus/plugin-content-docs/client';
import {
  extractLeadingEmoji,
  useDocCardDescriptionCategoryItemsPlural,
} from '@docusaurus/theme-common/internal';
import isInternalUrl from '@docusaurus/isInternalUrl';
import Layout from '@theme/DocCard/Layout';

import type {Props} from '@theme/DocCard';
import type {
  PropSidebarItemCategory,
  PropSidebarItemLink,
} from '@docusaurus/plugin-content-docs';

function getFallbackEmojiIcon(
  item: PropSidebarItemLink | PropSidebarItemCategory,
): string {
  const icon: string = item?.customProps?.emoji as string;
  if (item.type === 'category') {
    return icon ?? '';//'🗃';
  }
  return icon ?? '';//(isInternalUrl(item.href) ? '📄️' : '🔗');
}

function getBoxIcon(
  item: PropSidebarItemLink | PropSidebarItemCategory,
): ReactNode {
  const iconClass = item?.customProps?.boxicon as string | undefined;
  if (!iconClass) return null;

  return React.createElement('i', {
    className: `bx ${iconClass}`
  });
}

function getIconTitleProps(
  item: PropSidebarItemLink | PropSidebarItemCategory,
): {icon: ReactNode; title: string} {
  const extracted = extractLeadingEmoji(item.label);

  const boxicon = getBoxIcon(item);
  const icon = boxicon ?? extracted.emoji ?? getFallbackEmojiIcon(item);

  return {
    icon,
    title: extracted.rest.trim(),
  };
}

function CardCategory({item}: {item: PropSidebarItemCategory}): ReactNode {
  const href = findFirstSidebarItemLink(item);
  const categoryItemsPlural = useDocCardDescriptionCategoryItemsPlural();

  // Unexpected: categories that don't have a link have been filtered upfront
  if (!href) {
    return null;
  }
  return (
    <Layout
      item={item}
      className={item.className}
      href={href}
      description={item.description ?? categoryItemsPlural(item.items.length)}
      {...getIconTitleProps(item)}
    />
  );
}

function CardLink({item}: {item: PropSidebarItemLink}): ReactNode {
  const doc = useDocById(item.docId ?? undefined);
  return (
    <Layout
      item={item}
      className={item.className}
      href={item.href}
      description={item.description ?? doc?.description}
      {...getIconTitleProps(item)}
    />
  );
}

export default function DocCard({item}: Props): ReactNode {
  switch (item.type) {
    case 'link':
      return <CardLink item={item} />;
    case 'category':
      return <CardCategory item={item} />;
    default:
      throw new Error(`unknown item type ${JSON.stringify(item)}`);
  }
}
